package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.AttachmentTemplateCheckResult;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.AttachmentConfigStatusEnum;
import com.flowmind.platform.api.enums.HandleTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.OperationTargetTypeEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import com.flowmind.platform.api.request.AddSignRequest;
import com.flowmind.platform.api.request.ApproveTaskRequest;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.CheckAttachmentRequest;
import com.flowmind.platform.api.request.ClaimTaskRequest;
import com.flowmind.platform.api.request.DeleteProcessInstanceRequest;
import com.flowmind.platform.api.request.DirectSendRequest;
import com.flowmind.platform.api.request.RejectTaskRequest;
import com.flowmind.platform.api.request.ReturnTaskRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import com.flowmind.platform.api.request.StartProcessRequest;
import com.flowmind.platform.api.request.SubmitTaskRequest;
import com.flowmind.platform.api.request.TaskOperationRequest;
import com.flowmind.platform.api.request.TerminateProcessRequest;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.request.UnclaimTaskRequest;
import com.flowmind.platform.api.request.UpdateVariablesRequest;
import com.flowmind.platform.api.request.WithdrawTaskRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.dto.OperationResult;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.core.validation.DefinitionGraphIndex;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.HistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceDeletionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M2 流程运行时服务实现。
 *
 * <p>本类仅编排启动、提交、审批、变量更新和实例详情读取；定义选择、身份校验、幂等记录和
 * 节点推进均复用既有组件。幂等建租约在业务事务前完成；业务写入通过独立事务执行器提交，
 * 从而保证 Outbox 与运行时状态原子一致。</p>
 *
 * @author FlowMind
 * @since 2026-07-22
 */
@Service
public class DefaultProcessRuntimeService implements ProcessRuntimeService {

    /** 流程实例持久化仓储。 */
    private final ProcessInstanceRepository instanceRepository;
    /** 活动任务持久化仓储。 */
    private final ActiveTaskRepository activeTaskRepository;
    /** 历史任务持久化仓储。 */
    private final HistoryTaskRepository historyTaskRepository;
    /** 运行时定义读侧。 */
    private final RuntimeDefinitionLoader definitionLoader;
    /** 请求、身份、状态和权限校验器。 */
    private final RuntimeRequestValidator requestValidator;
    /** 幂等操作执行框架。 */
    private final RuntimeOperationExecutor operationExecutor;
    /** 统一节点推进器。 */
    private final RuntimeNodeAdvancer nodeAdvancer;
    /** C 线提供的运行状态前置校验器。 */
    private final RuntimeStateValidator runtimeStateValidator;
    /** C 线提供的幂等历史任务归档器。 */
    private final HistoryTaskWriter historyTaskWriter;
    /** 附件校验和保存正式服务，由 C 线提供实现。 */
    private final AttachmentService attachmentService;
    /** 回调 Outbox 正式服务，由 C 线提供实现。 */
    private final CallbackService callbackService;
    /** 承载运行时主数据库事务的独立执行组件。 */
    private final RuntimeTransactionExecutor transactionExecutor;
    /** 实例级管理动作的任务取消协调器。 */
    private final InstanceTaskCancellationService taskCancellationService;
    /** 实例级联删除持久化仓储。 */
    private final ProcessInstanceDeletionRepository instanceDeletionRepository;
    /** 复用既有审计日志写入原语。 */
    private final ProcessDefinitionRepository definitionRepository;

    /**
     * 创建 M2 运行时服务。
     */
    @Autowired
    public DefaultProcessRuntimeService(ProcessInstanceRepository instanceRepository,
                                        ActiveTaskRepository activeTaskRepository,
                                        HistoryTaskRepository historyTaskRepository,
                                        RuntimeDefinitionLoader definitionLoader,
                                        RuntimeRequestValidator requestValidator,
                                        RuntimeOperationExecutor operationExecutor,
                                        RuntimeNodeAdvancer nodeAdvancer,
                                        AttachmentService attachmentService,
                                         CallbackService callbackService,
                                         RuntimeStateValidator runtimeStateValidator,
                                         HistoryTaskWriter historyTaskWriter,
                                         RuntimeTransactionExecutor transactionExecutor,
                                         InstanceTaskCancellationService taskCancellationService,
                                         ProcessInstanceDeletionRepository instanceDeletionRepository,
                                         ProcessDefinitionRepository definitionRepository) {
        this.instanceRepository = instanceRepository;
        this.activeTaskRepository = activeTaskRepository;
        this.historyTaskRepository = historyTaskRepository;
        this.definitionLoader = definitionLoader;
        this.requestValidator = requestValidator;
        this.operationExecutor = operationExecutor;
        this.nodeAdvancer = nodeAdvancer;
        this.attachmentService = attachmentService;
        this.callbackService = callbackService;
        this.runtimeStateValidator = runtimeStateValidator;
        this.historyTaskWriter = historyTaskWriter;
        this.transactionExecutor = transactionExecutor;
        this.taskCancellationService = taskCancellationService;
        this.instanceDeletionRepository = instanceDeletionRepository;
        this.definitionRepository = definitionRepository;
    }

    /** 兼容 M2 测试和嵌入式调用的完整运行时构造器。 */
    public DefaultProcessRuntimeService(ProcessInstanceRepository instanceRepository,
                                        ActiveTaskRepository activeTaskRepository,
                                        HistoryTaskRepository historyTaskRepository,
                                        RuntimeDefinitionLoader definitionLoader,
                                        RuntimeRequestValidator requestValidator,
                                        RuntimeOperationExecutor operationExecutor,
                                        RuntimeNodeAdvancer nodeAdvancer,
                                        AttachmentService attachmentService,
                                        CallbackService callbackService,
                                        RuntimeStateValidator runtimeStateValidator,
                                        HistoryTaskWriter historyTaskWriter,
                                        RuntimeTransactionExecutor transactionExecutor) {
        this(instanceRepository, activeTaskRepository, historyTaskRepository, definitionLoader, requestValidator,
                operationExecutor, nodeAdvancer, attachmentService, callbackService, runtimeStateValidator,
                historyTaskWriter, transactionExecutor, null, null, null);
    }

    /** 兼容第五步前已存在的直接构造单元测试。 */
    public DefaultProcessRuntimeService(ProcessInstanceRepository instanceRepository,
                                        ActiveTaskRepository activeTaskRepository,
                                        HistoryTaskRepository historyTaskRepository,
                                        RuntimeDefinitionLoader definitionLoader,
                                        RuntimeRequestValidator requestValidator,
                                        RuntimeOperationExecutor operationExecutor,
                                        RuntimeNodeAdvancer nodeAdvancer,
                                        AttachmentService attachmentService,
                                        CallbackService callbackService,
                                        RuntimeStateValidator runtimeStateValidator,
                                        HistoryTaskWriter historyTaskWriter) {
        this(instanceRepository, activeTaskRepository, historyTaskRepository, definitionLoader, requestValidator,
                operationExecutor, nodeAdvancer, attachmentService, callbackService, runtimeStateValidator,
                historyTaskWriter, null, null, null, null);
    }

    /**
     * 兼容已有直接构造的单元测试；Spring 运行时始终使用完整构造器接入 C 线协作组件。
     */
    public DefaultProcessRuntimeService(ProcessInstanceRepository instanceRepository,
                                        ActiveTaskRepository activeTaskRepository,
                                        HistoryTaskRepository historyTaskRepository,
                                        RuntimeDefinitionLoader definitionLoader,
                                        RuntimeRequestValidator requestValidator,
                                        RuntimeOperationExecutor operationExecutor,
                                        RuntimeNodeAdvancer nodeAdvancer,
                                        AttachmentService attachmentService,
                                        CallbackService callbackService) {
        this(instanceRepository, activeTaskRepository, historyTaskRepository, definitionLoader, requestValidator,
                operationExecutor, nodeAdvancer, attachmentService, callbackService, null, null, null, null, null,
                null);
    }

    /** {@inheritDoc} */
    @Override
    public ProcessInstanceDTO startProcess(StartProcessRequest request) {
        UserContext starter = requestValidator.validateStart(request);
        OperationIdempotencyDecision decision = operationExecutor.begin(request, RuntimeOperationTypes.START_PROCESS,
                starter.getUserId(), null, null, LocalDateTime.now());
        if (isSuccessfulReplay(decision)) {
            return operationExecutor.replayResult(decision, ProcessInstanceDTO.class);
        }
        operationExecutor.assertExecutable(decision);
        try {
            return executeInTransaction(new RuntimeTransactionWork<ProcessInstanceDTO>() {
                @Override
                public ProcessInstanceDTO execute() {
                    ProcessDefinitionDetailDTO definition = definitionLoader.loadForStart(request.getProcessCode());
                    ProcessInstanceEntity instance = newInstance(request, starter, definition,
                            InstanceStatusEnum.NOT_STARTED, null);
                    insertInstance(instance);
                    operationExecutor.bindTarget(request.getOperationId(), instance.getId(), null);

                    ProcessInstanceDTO result = RuntimeModelMapper.toDto(instance);
                    result.setCreatedTasks(Collections.<TaskDTO>emptyList());
                    operationExecutor.markSuccess(request.getOperationId(), result);
                    return result;
                }
            });
        } catch (RuntimeValidationException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        } catch (RuntimeStateException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        }
    }

    /** {@inheritDoc} */
    @Override
    public ProcessInstanceDTO startAndSubmit(StartProcessRequest request) {
        UserContext starter = requestValidator.validateStart(request);
        OperationIdempotencyDecision decision = operationExecutor.begin(request, RuntimeOperationTypes.START_AND_SUBMIT,
                starter.getUserId(), null, null, LocalDateTime.now());
        if (isSuccessfulReplay(decision)) {
            return operationExecutor.replayResult(decision, ProcessInstanceDTO.class);
        }
        operationExecutor.assertExecutable(decision);
        try {
            return executeInTransaction(new RuntimeTransactionWork<ProcessInstanceDTO>() {
                @Override
                public ProcessInstanceDTO execute() {
                    ProcessDefinitionDetailDTO definition = definitionLoader.loadForStart(request.getProcessCode());
                    ProcessInstanceEntity instance = newInstance(request, starter, definition, InstanceStatusEnum.RUNNING,
                            LocalDateTime.now());
                    String startNodeCode = startTargetNodeCode(definition);
                    RuntimeAdvancePreparation preparation = nodeAdvancer.prepareAdvance(instance, definition,
                            startNodeCode, null, null);
                    insertInstance(instance);
                    operationExecutor.bindTarget(request.getOperationId(), instance.getId(), null);

                    RuntimeAdvanceResult advanceResult = nodeAdvancer.advanceToNode(instance, definition,
                            startNodeCode, null, null, preparation);
                    ProcessInstanceDTO result = toInstanceResult(instance.getId(), advanceResult.getCreatedTasks());
                    publishStartEvents(request.getOperationId(), result, starter, advanceResult);
                    operationExecutor.markSuccess(request.getOperationId(), result);
                    return result;
                }
            });
        } catch (RuntimeValidationException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        } catch (RuntimeStateException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        }
    }

    /** {@inheritDoc} */
    @Override
    public TaskActionResult submitTask(SubmitTaskRequest request) {
        return handleTaskAction(request, ActionTypeEnum.SEND, true);
    }

    /** {@inheritDoc} */
    @Override
    public TaskActionResult approve(ApproveTaskRequest request) {
        return handleTaskAction(request, ActionTypeEnum.APPROVE, false);
    }

    /** {@inheritDoc} */
    @Override
    public ProcessInstanceDTO updateVariables(UpdateVariablesRequest request) {
        UserContext operator = requestValidator.validateVariableUpdateIdentity(request);
        OperationIdempotencyDecision decision = operationExecutor.begin(request, RuntimeOperationTypes.UPDATE_VARIABLES,
                operator.getUserId(), request.getInstanceId(), null, LocalDateTime.now());
        if (isSuccessfulReplay(decision)) {
            return operationExecutor.replayResult(decision, ProcessInstanceDTO.class);
        }
        operationExecutor.assertExecutable(decision);
        try {
            return executeInTransaction(new RuntimeTransactionWork<ProcessInstanceDTO>() {
                @Override
                public ProcessInstanceDTO execute() {
                    ProcessInstanceEntity instance = instanceRepository.findById(request.getInstanceId());
                    requestValidator.validateVariableUpdate(request, instance, operator);
                    Map<String, Object> variables = mergeVariables(instance.getVariablesJson(), request.getVariables());
                    String variablesJson = RuntimeJsonCodec.toJson(variables);
                    if (instanceRepository.updateVariablesJson(instance.getId(), variablesJson) != 1) {
                        throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                                "process instance variables cannot be updated");
                    }
                    instance.setVariablesJson(variablesJson);
                    ProcessInstanceDTO result = RuntimeModelMapper.toDto(instance);
                    result.setCreatedTasks(Collections.<TaskDTO>emptyList());
                    operationExecutor.markSuccess(request.getOperationId(), result);
                    return result;
                }
            });
        } catch (RuntimeValidationException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        } catch (RuntimeStateException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        }
    }

    /** {@inheritDoc} */
    @Override
    public ProcessInstanceDetailDTO getInstance(String instanceId) {
        if (isBlank(instanceId)) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "instanceId is required");
        }
        ProcessInstanceEntity instance = instanceRepository.findById(instanceId);
        if (instance == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "process instance does not exist");
        }
        ProcessDefinitionDetailDTO definition = definitionLoader.loadForInstance(instance);
        DefinitionGraphIndex graph = DefinitionGraphIndex.from(definition);

        ProcessInstanceDetailDTO detail = new ProcessInstanceDetailDTO();
        copyInstance(RuntimeModelMapper.toDto(instance), detail);
        detail.setCreatedTasks(Collections.<TaskDTO>emptyList());
        detail.setActiveTasks(toActiveTaskDtos(activeTaskRepository.findOpenByInstanceId(instanceId), graph));

        List<HistoryTaskDTO> historyTasks = new ArrayList<HistoryTaskDTO>();
        for (ProcessHistoryTaskEntity history : historyTaskRepository.findByInstanceId(instanceId)) {
            historyTasks.add(RuntimeModelMapper.toDto(history));
        }
        detail.setHistoryTasks(historyTasks);
        detail.setComments(toComments(historyTasks));
        return detail;
    }

    /** M3 起提供驳回能力。 */
    @Override
    public TaskActionResult reject(RejectTaskRequest request) {
        throw unsupported("reject");
    }

    /** M3 起提供退回能力。 */
    @Override
    public TaskActionResult returnToStarter(ReturnTaskRequest request) {
        throw unsupported("returnToStarter");
    }

    /** M5 起提供撤回能力。 */
    @Override
    public TaskActionResult withdraw(WithdrawTaskRequest request) {
        throw unsupported("withdraw");
    }

    /** M5 起提供直送能力。 */
    @Override
    public TaskActionResult directSend(DirectSendRequest request) {
        throw unsupported("directSend");
    }

    /** M5 起提供转办能力。 */
    @Override
    public TaskActionResult transfer(TransferTaskRequest request) {
        throw unsupported("transfer");
    }

    /** M5 起提供加签能力。 */
    @Override
    public TaskActionResult addSign(AddSignRequest request) {
        throw unsupported("addSign");
    }

    /** M5 起提供认领能力。 */
    @Override
    public TaskActionResult claim(ClaimTaskRequest request) {
        throw unsupported("claim");
    }

    /** M5 起提供取消认领能力。 */
    @Override
    public TaskActionResult unclaim(UnclaimTaskRequest request) {
        throw unsupported("unclaim");
    }

    /** 终止运行中的流程实例，并取消当前全部开放工作。 */
    @Override
    public ProcessInstanceDTO terminate(TerminateProcessRequest request) {
        UserContext operator = requestValidator.validateInstanceOperationIdentity(request, request.getInstanceId(),
                request.getOperatorUserId());
        OperationIdempotencyDecision decision = operationExecutor.begin(request, RuntimeOperationTypes.TERMINATE,
                operator.getUserId(), request.getInstanceId(), null, LocalDateTime.now());
        if (isSuccessfulReplay(decision)) {
            return operationExecutor.replayResult(decision, ProcessInstanceDTO.class);
        }
        operationExecutor.assertExecutable(decision);
        try {
            requireTerminateDependencies();
            return executeInTransaction(new RuntimeTransactionWork<ProcessInstanceDTO>() {
                @Override
                public ProcessInstanceDTO execute() {
                    ProcessInstanceEntity instance = requireRunningInstance(request.getInstanceId());
                    LocalDateTime endedAt = LocalDateTime.now();
                    if (instanceRepository.terminate(instance.getId(), endedAt) != 1) {
                        throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_STATUS_INVALID,
                                "process instance cannot be terminated");
                    }
                    instance.setInstanceStatus(InstanceStatusEnum.TERMINATED.name());
                    instance.setCurrentNodeCodes("[]");
                    instance.setEndedAt(endedAt);
                    List<HistoryTaskDTO> archivedTasks = taskCancellationService.cancelOpenWork(instance, operator,
                            ActionTypeEnum.TERMINATE, request.getComment(), request.getOperationId(),
                            readVariables(instance.getVariablesJson()));
                    ProcessInstanceDTO result = RuntimeModelMapper.toDto(instance);
                    result.setCreatedTasks(Collections.<TaskDTO>emptyList());
                    writeInstanceAudit(instance, operator, request.getOperationId(), ActionTypeEnum.TERMINATE,
                            request.getComment(), archivedTasks.size());
                    publishEvent(request.getOperationId(), WorkflowEventTypeEnum.PROCESS_TERMINATED,
                            instance.getId(), ActionTypeEnum.TERMINATE, result, operator, archivedTasks,
                            Collections.<TaskDTO>emptyList());
                    operationExecutor.markSuccess(request.getOperationId(), result);
                    return result;
                }
            });
        } catch (RuntimeValidationException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        } catch (RuntimeStateException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        }
    }

    /** 物理删除流程实例及其运行数据，保留日志和幂等记录。 */
    @Override
    public OperationResult deleteInstance(DeleteProcessInstanceRequest request) {
        UserContext operator = requestValidator.validateInstanceOperationIdentity(request, request.getInstanceId(),
                request.getOperatorUserId());
        OperationIdempotencyDecision decision = operationExecutor.begin(request, RuntimeOperationTypes.DELETE_INSTANCE,
                operator.getUserId(), request.getInstanceId(), null, LocalDateTime.now());
        if (isSuccessfulReplay(decision)) {
            OperationResult replayed = operationExecutor.replayResult(decision, OperationResult.class);
            replayed.setReplayed(true);
            return replayed;
        }
        operationExecutor.assertExecutable(decision);
        try {
            requireDeleteDependencies();
            return executeInTransaction(new RuntimeTransactionWork<OperationResult>() {
                @Override
                public OperationResult execute() {
                    ProcessInstanceEntity instance = instanceRepository.findById(request.getInstanceId());
                    if (instance == null) {
                        throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_NOT_FOUND,
                                "process instance does not exist");
                    }
                    operationExecutor.bindTarget(request.getOperationId(), instance.getId(), null);
                    ProcessInstanceDTO snapshot = RuntimeModelMapper.toDto(instance);
                    snapshot.setCreatedTasks(Collections.<TaskDTO>emptyList());
                    writeInstanceAudit(instance, operator, request.getOperationId(), ActionTypeEnum.CANCEL,
                            "hard delete instance", 0);
                    publishEvent(request.getOperationId(), WorkflowEventTypeEnum.PROCESS_CANCELED, instance.getId(),
                            ActionTypeEnum.CANCEL, snapshot, operator, Collections.<HistoryTaskDTO>emptyList(),
                            Collections.<TaskDTO>emptyList());
                    if (instanceDeletionRepository.deleteRuntimeData(instance.getId()) != 1) {
                        throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_NOT_FOUND,
                                "process instance disappeared while deleting");
                    }
                    OperationResult result = new OperationResult();
                    result.setOperationId(request.getOperationId());
                    result.setTargetType(OperationTargetTypeEnum.INSTANCE);
                    result.setTargetId(instance.getId());
                    result.setDeleted(true);
                    result.setReplayed(false);
                    operationExecutor.markSuccess(request.getOperationId(), result);
                    return result;
                }
            });
        } catch (RuntimeValidationException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        } catch (RuntimeStateException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        }
    }

    private TaskActionResult handleTaskAction(TaskOperationRequest request,
                                                ActionTypeEnum actionType,
                                                boolean submitStarterTask) {
        UserContext operator = requestValidator.validateTaskIdentity(request);
        OperationIdempotencyDecision decision = operationExecutor.begin(request, operationType(actionType),
                operator.getUserId(), null, request.getTaskId(), LocalDateTime.now());
        if (isSuccessfulReplay(decision)) {
            return operationExecutor.replayTaskAction(decision);
        }
        operationExecutor.assertExecutable(decision);
        try {
            return executeInTransaction(new RuntimeTransactionWork<TaskActionResult>() {
                @Override
                public TaskActionResult execute() {
                    return handleTaskActionInTransaction(request, actionType, submitStarterTask, operator);
                }
            });
        } catch (RuntimeValidationException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        } catch (RuntimeStateException ex) {
            markDeterministicFailure(request.getOperationId(), ex);
            throw ex;
        }
    }

    private TaskActionResult handleTaskActionInTransaction(TaskOperationRequest request,
                                                            ActionTypeEnum actionType,
                                                            boolean submitStarterTask,
                                                            UserContext operator) {
        RuntimeTaskContext taskContext = null;
        ProcessActiveTaskEntity task;
        ProcessInstanceEntity instance;
        if (runtimeStateValidator != null) {
            taskContext = runtimeStateValidator.validateTaskAction(request.getTaskId(),
                    request.getExpectedTaskVersion(), actionType, operator);
            task = taskContext.getTask();
            instance = taskContext.getInstance();
        } else {
            task = activeTaskRepository.findById(request.getTaskId());
            instance = task == null ? null : instanceRepository.findById(task.getInstanceId());
        }
        requestValidator.validateTaskAction(request, instance, task, operator);
        operationExecutor.bindTarget(request.getOperationId(), instance.getId(), task.getId());

        ProcessDefinitionDetailDTO definition = definitionLoader.loadForInstance(instance);
        ProcessNodeDTO node = requireUserTaskNode(definition, task.getNodeCode());
        assertTaskActionNode(node, submitStarterTask);
        if (submitStarterTask) {
            prepareSubmitAttachments((SubmitTaskRequest) request, instance, task);
        }

        Map<String, Object> variables = submitStarterTask
                ? mergeVariables(instance.getVariablesJson(), ((SubmitTaskRequest) request).getVariables())
                : readVariables(instance.getVariablesJson());
        instance.setVariablesJson(RuntimeJsonCodec.toJson(variables));
        String targetNodeCode = singleOutgoingTarget(definition, node.getNodeCode());
        RuntimeAdvancePreparation preparation = nodeAdvancer.prepareAdvance(instance, definition, targetNodeCode,
                task.getTaskGroupId(), task.getBranchKey());

        if (activeTaskRepository.complete(task.getId(), request.getExpectedTaskVersion().longValue()) != 1) {
            throw new RuntimeStateException(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED,
                    "active task was modified by another request");
        }
        if (submitStarterTask && instanceRepository.updateVariablesJson(instance.getId(),
                RuntimeJsonCodec.toJson(variables)) != 1) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                    "process instance variables cannot be updated");
        }
        HistoryTaskDTO archivedTask = archiveTask(taskContext, task, instance, request, operator, actionType,
                variables);
        RuntimeAdvanceResult advanceResult = nodeAdvancer.advanceToNode(instance, definition, targetNodeCode,
                task.getTaskGroupId(), task.getBranchKey(), preparation);
        TaskActionResult result = new TaskActionResult();
        result.setOperationId(request.getOperationId());
        result.setArchivedTasks(Collections.singletonList(archivedTask));
        result.setCreatedTasks(new ArrayList<TaskDTO>(advanceResult.getCreatedTasks()));
        result.setInstance(toInstanceResult(instance.getId(), advanceResult.getCreatedTasks()));
        publishTaskActionEvents(request.getOperationId(), actionType, result, operator, advanceResult);
        operationExecutor.markSuccess(request.getOperationId(), result);
        return result;
    }

    private ProcessInstanceEntity newInstance(StartProcessRequest request,
                                               UserContext starter,
                                               ProcessDefinitionDetailDTO definition,
                                               InstanceStatusEnum status,
                                               LocalDateTime startedAt) {
        ProcessInstanceEntity instance = new ProcessInstanceEntity();
        instance.setId(UUID.randomUUID().toString());
        instance.setDefinitionId(definition.getId());
        instance.setAttachmentConfigId(activeAttachmentConfigId(definition));
        instance.setProcessCode(definition.getProcessCode());
        instance.setProcessName(definition.getProcessName());
        instance.setVersion(definition.getVersion());
        instance.setInstanceTitle(request.getInstanceTitle());
        instance.setBusinessKey(request.getBusinessKey());
        instance.setStarterUserId(starter.getUserId());
        instance.setStarterUserName(starter.getUserName());
        instance.setStarterDeptId(starter.getDepartmentId());
        instance.setCurrentNodeCodes(RuntimeJsonCodec.toJson(new ArrayList<String>()));
        instance.setVariablesJson(RuntimeJsonCodec.toJson(copyVariables(request.getVariables())));
        instance.setInstanceStatus(status.name());
        instance.setStartedAt(startedAt);
        return instance;
    }

    private void insertInstance(ProcessInstanceEntity instance) {
        if (instanceRepository.insert(instance) != 1) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "failed to create process instance");
        }
    }

    private String activeAttachmentConfigId(ProcessDefinitionDetailDTO definition) {
        Set<String> activeConfigIds = new LinkedHashSet<String>();
        if (definition.getAttachmentTemplates() != null) {
            for (ProcessAttachmentTemplateDTO template : definition.getAttachmentTemplates()) {
                if (template != null && AttachmentConfigStatusEnum.ACTIVE.equals(template.getConfigStatus())) {
                    if (isBlank(template.getAttachmentConfigId())) {
                        throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                                "active attachment configuration has no attachmentConfigId");
                    }
                    activeConfigIds.add(template.getAttachmentConfigId());
                }
            }
        }
        if (activeConfigIds.size() > 1) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "definition has multiple active attachment configuration groups");
        }
        return activeConfigIds.isEmpty() ? null : activeConfigIds.iterator().next();
    }

    private String startTargetNodeCode(ProcessDefinitionDetailDTO definition) {
        DefinitionGraphIndex graph = DefinitionGraphIndex.from(definition);
        if (graph.getStartNodes().size() != 1) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "definition must contain exactly one start node");
        }
        return singleOutgoingTarget(graph, graph.getStartNodes().get(0).getNodeCode());
    }

    private String singleOutgoingTarget(ProcessDefinitionDetailDTO definition, String nodeCode) {
        return singleOutgoingTarget(DefinitionGraphIndex.from(definition), nodeCode);
    }

    private String singleOutgoingTarget(DefinitionGraphIndex graph, String nodeCode) {
        List<ProcessEdgeDTO> outgoing = graph.getOutgoingEdges(nodeCode);
        if (outgoing.size() != 1 || isBlank(outgoing.get(0).getTargetNodeCode())) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "node must have exactly one valid outgoing edge: " + nodeCode);
        }
        return outgoing.get(0).getTargetNodeCode();
    }

    private ProcessNodeDTO requireUserTaskNode(ProcessDefinitionDetailDTO definition, String nodeCode) {
        ProcessNodeDTO node = DefinitionGraphIndex.from(definition).getNodesByCode().get(nodeCode);
        if (node == null || node.getNodeType() == null
                || !com.flowmind.platform.api.enums.NodeTypeEnum.USER_TASK.equals(node.getNodeType())) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "active task node is not a user task: " + nodeCode);
        }
        return node;
    }

    private void assertTaskActionNode(ProcessNodeDTO node, boolean submitStarterTask) {
        boolean isStarterTask = ApproverRuleTypeEnum.STARTER.equals(node.getApproverRuleType());
        if (submitStarterTask && !isStarterTask) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "submitTask only supports STARTER user tasks in M2");
        }
        if (!submitStarterTask && isStarterTask) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "approve does not support STARTER user tasks in M2");
        }
    }

    private void prepareSubmitAttachments(SubmitTaskRequest request,
                                          ProcessInstanceEntity instance,
                                          ProcessActiveTaskEntity task) {
        if (attachmentService == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "attachment service is unavailable");
        }
        if (request.getAttachments() != null) {
            for (AttachmentUploadItem attachment : request.getAttachments()) {
                if (attachment == null) {
                    throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                            "submit attachments must not contain null items");
                }
                attachmentService.saveTaskAttachment(toSaveTaskAttachmentRequest(request, instance, attachment));
            }
        }
        CheckAttachmentRequest checkRequest = new CheckAttachmentRequest();
        checkRequest.setInstanceId(instance.getId());
        checkRequest.setNodeCode(task.getNodeCode());
        checkRequest.setOperatorUserId(request.getOperatorUserId());
        AttachmentTemplateCheckResult checkResult = attachmentService.checkRequiredAttachments(checkRequest);
        if (checkResult == null || !checkResult.isPassed()) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "required attachments are not satisfied for node: " + task.getNodeCode());
        }
    }

    private SaveTaskAttachmentRequest toSaveTaskAttachmentRequest(SubmitTaskRequest request,
                                                                   ProcessInstanceEntity instance,
                                                                   AttachmentUploadItem attachment) {
        SaveTaskAttachmentRequest saveRequest = new SaveTaskAttachmentRequest();
        saveRequest.setOperationId(request.getOperationId());
        saveRequest.setTaskId(request.getTaskId());
        saveRequest.setExpectedTaskVersion(request.getExpectedTaskVersion());
        saveRequest.setOperatorUserId(request.getOperatorUserId());
        saveRequest.setComment(request.getComment());
        saveRequest.setInstanceId(instance.getId());
        saveRequest.setAttachment(attachment);
        return saveRequest;
    }

    private HistoryTaskDTO archiveTask(RuntimeTaskContext taskContext,
                                       ProcessActiveTaskEntity task,
                                       ProcessInstanceEntity instance,
                                       TaskOperationRequest request,
                                       UserContext operator,
                                       ActionTypeEnum actionType,
                                       Map<String, Object> variables) {
        if (historyTaskWriter != null && taskContext != null) {
            ProcessHistoryTaskEntity history = historyTaskWriter.archiveCompletedTask(taskContext, actionType,
                    request.getComment(), variables, request.getOperationId());
            return RuntimeModelMapper.toDto(history);
        }
        ProcessHistoryTaskEntity history = new ProcessHistoryTaskEntity();
        history.setId(UUID.randomUUID().toString());
        history.setInstanceId(instance.getId());
        history.setOperationId(request.getOperationId());
        history.setActiveTaskId(task.getId());
        history.setNodeCode(task.getNodeCode());
        history.setTaskGroupId(task.getTaskGroupId());
        history.setBranchKey(task.getBranchKey());
        history.setAssigneeUserId(operator.getUserId());
        history.setAssigneeUserName(operator.getUserName());
        history.setDelegateFromUserId(task.getDelegateFromUserId());
        history.setHandleType(HandleTypeEnum.NORMAL.name());
        history.setActionType(actionType.name());
        history.setCommentText(request.getComment());
        history.setVariablesSnapshot(RuntimeJsonCodec.toJson(variables));
        history.setStartedAt(task.getCreatedAt());
        history.setCompletedAt(LocalDateTime.now());
        if (historyTaskRepository.insert(history) != 1) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "failed to archive completed task");
        }
        return RuntimeModelMapper.toDto(history);
    }

    private ProcessInstanceDTO toInstanceResult(String instanceId, List<TaskDTO> createdTasks) {
        ProcessInstanceEntity persisted = instanceRepository.findById(instanceId);
        if (persisted == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "process instance disappeared");
        }
        ProcessInstanceDTO result = RuntimeModelMapper.toDto(persisted);
        result.setCreatedTasks(new ArrayList<TaskDTO>(createdTasks));
        return result;
    }

    private List<TaskDTO> toActiveTaskDtos(List<ProcessActiveTaskEntity> activeTasks, DefinitionGraphIndex graph) {
        List<TaskDTO> results = new ArrayList<TaskDTO>();
        if (activeTasks == null) {
            return results;
        }
        for (ProcessActiveTaskEntity task : activeTasks) {
            ProcessNodeDTO node = graph.getNodesByCode().get(task.getNodeCode());
            results.add(RuntimeModelMapper.toDto(task, node == null ? null : node.getNodeName(), null));
        }
        return results;
    }

    private List<ProcessCommentDTO> toComments(List<HistoryTaskDTO> historyTasks) {
        List<ProcessCommentDTO> comments = new ArrayList<ProcessCommentDTO>();
        for (HistoryTaskDTO history : historyTasks) {
            if (!isBlank(history.getComment())) {
                ProcessCommentDTO comment = new ProcessCommentDTO();
                comment.setCommentId(history.getHistoryTaskId());
                comment.setInstanceId(history.getInstanceId());
                comment.setTaskId(history.getActiveTaskId());
                comment.setNodeCode(history.getNodeCode());
                comment.setOperatorUserId(history.getAssigneeUserId());
                comment.setOperatorUserName(history.getAssigneeUserName());
                comment.setComment(history.getComment());
                comment.setCreatedAt(history.getCompletedAt());
                comments.add(comment);
            }
        }
        return comments;
    }

    private void publishStartEvents(String operationId,
                                    ProcessInstanceDTO instance,
                                    UserContext starter,
                                    RuntimeAdvanceResult advanceResult) {
        publishEvent(operationId, WorkflowEventTypeEnum.PROCESS_STARTED, instance.getInstanceId(),
                ActionTypeEnum.START, instance, starter, Collections.<HistoryTaskDTO>emptyList(),
                advanceResult.getCreatedTasks());
        publishCreatedTaskEvents(operationId, ActionTypeEnum.START, instance, starter,
                advanceResult.getCreatedTasks());
        if (advanceResult.isInstanceCompleted()) {
            publishEvent(operationId, WorkflowEventTypeEnum.PROCESS_COMPLETED, instance.getInstanceId(),
                    ActionTypeEnum.START, instance, starter, Collections.<HistoryTaskDTO>emptyList(),
                    Collections.<TaskDTO>emptyList());
        }
    }

    private void publishTaskActionEvents(String operationId,
                                          ActionTypeEnum actionType,
                                          TaskActionResult result,
                                          UserContext operator,
                                          RuntimeAdvanceResult advanceResult) {
        WorkflowEventTypeEnum eventType = ActionTypeEnum.SEND.equals(actionType)
                ? WorkflowEventTypeEnum.TASK_SUBMITTED : WorkflowEventTypeEnum.TASK_COMPLETED;
        String targetId = result.getArchivedTasks().get(0).getActiveTaskId();
        publishEvent(operationId, eventType, targetId, actionType, result.getInstance(), operator,
                result.getArchivedTasks(), result.getCreatedTasks());
        publishCreatedTaskEvents(operationId, actionType, result.getInstance(), operator, result.getCreatedTasks());
        if (advanceResult.isInstanceCompleted()) {
            publishEvent(operationId, WorkflowEventTypeEnum.PROCESS_COMPLETED,
                    result.getInstance().getInstanceId(), actionType, result.getInstance(), operator,
                    result.getArchivedTasks(), Collections.<TaskDTO>emptyList());
        }
    }

    private void publishCreatedTaskEvents(String operationId,
                                          ActionTypeEnum actionType,
                                          ProcessInstanceDTO instance,
                                          UserContext operator,
                                          List<TaskDTO> createdTasks) {
        for (TaskDTO task : createdTasks) {
            publishEvent(operationId, WorkflowEventTypeEnum.TASK_CREATED, task.getTaskId(), actionType,
                    instance, operator, Collections.<HistoryTaskDTO>emptyList(),
                    Collections.singletonList(task));
        }
    }

    private void publishEvent(String operationId,
                              WorkflowEventTypeEnum eventType,
                              String targetId,
                              ActionTypeEnum actionType,
                              ProcessInstanceDTO instance,
                              UserContext operator,
                              List<HistoryTaskDTO> archivedTasks,
                              List<TaskDTO> createdTasks) {
        if (callbackService == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION, "callback service is unavailable");
        }
        WorkflowEvent event = new WorkflowEvent();
        event.setEventId(operationId + ":" + eventType.name() + ":" + targetId);
        event.setOperationId(operationId);
        event.setEventType(eventType);
        event.setProcessCode(instance.getProcessCode());
        event.setInstanceId(instance.getInstanceId());
        event.setActionType(actionType);
        event.setOperator(copyUser(operator));
        event.setArchivedTasks(new ArrayList<HistoryTaskDTO>(archivedTasks));
        event.setCreatedTasks(new ArrayList<TaskDTO>(createdTasks));
        event.setVariables(new LinkedHashMap<String, Object>(instance.getVariables()));
        event.setOccurredAt(LocalDateTime.now());
        callbackService.publishCallback(event);
    }

    private Map<String, Object> mergeVariables(String variablesJson, Map<String, Object> updates) {
        Map<String, Object> merged = readVariables(variablesJson);
        if (updates != null) {
            merged.putAll(updates);
        }
        return merged;
    }

    private Map<String, Object> copyVariables(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(source);
    }

    private Map<String, Object> readVariables(String variablesJson) {
        try {
            return RuntimeJsonCodec.readObjectMap(variablesJson);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "instance variables are malformed");
        }
    }

    private void copyInstance(ProcessInstanceDTO source, ProcessInstanceDetailDTO target) {
        target.setInstanceId(source.getInstanceId());
        target.setDefinitionId(source.getDefinitionId());
        target.setAttachmentConfigId(source.getAttachmentConfigId());
        target.setProcessCode(source.getProcessCode());
        target.setProcessName(source.getProcessName());
        target.setVersion(source.getVersion());
        target.setInstanceTitle(source.getInstanceTitle());
        target.setBusinessKey(source.getBusinessKey());
        target.setStarterUserId(source.getStarterUserId());
        target.setStarterUserName(source.getStarterUserName());
        target.setStarterDeptId(source.getStarterDeptId());
        target.setInstanceStatus(source.getInstanceStatus());
        target.setCurrentNodeCodes(source.getCurrentNodeCodes());
        target.setVariables(source.getVariables());
        target.setStartedAt(source.getStartedAt());
        target.setEndedAt(source.getEndedAt());
    }

    private UserContext copyUser(UserContext source) {
        return source == null ? null : new UserContext(source.getUserId(), source.getUserName(),
                source.getDepartmentId(), source.getDepartmentName());
    }

    private boolean isSuccessfulReplay(OperationIdempotencyDecision decision) {
        return decision != null && OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType());
    }

    private String operationType(ActionTypeEnum actionType) {
        if (ActionTypeEnum.SEND.equals(actionType)) {
            return RuntimeOperationTypes.SUBMIT_TASK;
        }
        if (ActionTypeEnum.APPROVE.equals(actionType)) {
            return RuntimeOperationTypes.APPROVE_TASK;
        }
        throw new IllegalArgumentException("M2 task operation type is unsupported: " + actionType);
    }

    private <T> T executeInTransaction(RuntimeTransactionWork<T> work) {
        if (transactionExecutor == null) {
            // 保留旧单元测试的直接构造兼容性；Spring 生产构造器始终注入独立执行器。
            return work.execute();
        }
        return transactionExecutor.execute(work);
    }

    private void markDeterministicFailure(String operationId, RuntimeValidationException exception) {
        operationExecutor.markDeterministicFailure(operationId, exception.getErrorCode());
    }

    private void markDeterministicFailure(String operationId, RuntimeStateException exception) {
        operationExecutor.markDeterministicFailure(operationId, exception.getErrorCode());
    }

    /** 读取并校验实例仍处于运行态。 */
    private ProcessInstanceEntity requireRunningInstance(String instanceId) {
        ProcessInstanceEntity instance = instanceRepository.findById(instanceId);
        if (instance == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_NOT_FOUND, "process instance does not exist");
        }
        if (!InstanceStatusEnum.RUNNING.name().equals(instance.getInstanceStatus())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_STATUS_INVALID,
                    "process instance is not running");
        }
        return instance;
    }

    /** 写入实例级管理动作审计记录。 */
    private void writeInstanceAudit(ProcessInstanceEntity instance,
                                    UserContext operator,
                                    String operationId,
                                    ActionTypeEnum actionType,
                                    String comment,
                                    int archivedTaskCount) {
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("comment", comment);
        detail.put("archivedTaskCount", Integer.valueOf(archivedTaskCount));
        if (definitionRepository.insertAuditLog(UUID.randomUUID().toString(), instance.getId(), operationId,
                OperationTargetTypeEnum.INSTANCE.name(), instance.getId(), actionType.name(), operator.getUserId(),
                RuntimeJsonCodec.toJson(detail), LocalDateTime.now()) != 1) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                    "failed to write instance management audit log");
        }
    }

    /** M3 管理动作依赖必须由 Spring 正式构造器注入。 */
    private void requireTerminateDependencies() {
        if (taskCancellationService == null || definitionRepository == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                    "M3 terminate dependencies are unavailable");
        }
    }

    private void requireDeleteDependencies() {
        if (instanceDeletionRepository == null || definitionRepository == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                    "M3 instance deletion dependencies are unavailable");
        }
    }

    private UnsupportedOperationException unsupported(String operationName) {
        return new UnsupportedOperationException(operationName + " is not implemented in the current milestone");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
