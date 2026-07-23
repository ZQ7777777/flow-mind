package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.AdminHistoryTaskQuery;
import com.flowmind.platform.api.dto.AdminInstanceQuery;
import com.flowmind.platform.api.dto.AdminTaskQuery;
import com.flowmind.platform.api.dto.AuditLogDTO;
import com.flowmind.platform.api.dto.AuditLogQuery;
import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.enums.OperationTargetTypeEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import com.flowmind.platform.api.request.ForceCompleteRequest;
import com.flowmind.platform.api.request.JumpNodeRequest;
import com.flowmind.platform.api.service.AdminProcessService;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.core.validation.DefinitionGraphIndex;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ProcessDefinitionRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M3 管理端实例命令实现。
 *
 * <p>本类只实现跳转和强制办结两类写命令。管理员查询由 C 线查询服务补齐，避免命令侧
 * 重复实现读模型和分页 SQL。</p>
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@Service
public class DefaultAdminProcessService implements AdminProcessService {

    private final ProcessInstanceRepository instanceRepository;
    private final RuntimeDefinitionLoader definitionLoader;
    private final RuntimeRequestValidator requestValidator;
    private final RuntimeOperationExecutor operationExecutor;
    private final RuntimeNodeAdvancer nodeAdvancer;
    private final InstanceTaskCancellationService taskCancellationService;
    private final ProcessDefinitionRepository definitionRepository;
    private final CallbackService callbackService;
    private final RuntimeTransactionExecutor transactionExecutor;

    /** 创建 M3 管理端实例命令服务。 */
    public DefaultAdminProcessService(ProcessInstanceRepository instanceRepository,
                                      RuntimeDefinitionLoader definitionLoader,
                                      RuntimeRequestValidator requestValidator,
                                      RuntimeOperationExecutor operationExecutor,
                                      RuntimeNodeAdvancer nodeAdvancer,
                                      InstanceTaskCancellationService taskCancellationService,
                                      ProcessDefinitionRepository definitionRepository,
                                      CallbackService callbackService,
                                      RuntimeTransactionExecutor transactionExecutor) {
        this.instanceRepository = instanceRepository;
        this.definitionLoader = definitionLoader;
        this.requestValidator = requestValidator;
        this.operationExecutor = operationExecutor;
        this.nodeAdvancer = nodeAdvancer;
        this.taskCancellationService = taskCancellationService;
        this.definitionRepository = definitionRepository;
        this.callbackService = callbackService;
        this.transactionExecutor = transactionExecutor;
    }

    /** {@inheritDoc} */
    @Override
    public TaskActionResult jumpToNode(final JumpNodeRequest request) {
        final UserContext operator = requestValidator.validateInstanceOperationIdentity(request, request.getInstanceId(),
                request.getOperatorUserId());
        OperationIdempotencyDecision decision = operationExecutor.begin(request, RuntimeOperationTypes.JUMP,
                operator.getUserId(), request.getInstanceId(), null, LocalDateTime.now());
        if (isSuccessfulReplay(decision)) {
            return operationExecutor.replayTaskAction(decision);
        }
        operationExecutor.assertExecutable(decision);
        try {
            return transactionExecutor.execute(new RuntimeTransactionWork<TaskActionResult>() {
                @Override
                public TaskActionResult execute() {
                    ProcessInstanceEntity instance = requireJumpableInstance(request.getInstanceId());
                    ProcessDefinitionDetailDTO definition = definitionLoader.loadForInstance(instance);
                    assertJumpTarget(definition, request.getTargetNodeCode());
                    RuntimeAdvancePreparation preparation = nodeAdvancer.prepareAdvance(instance, definition,
                            request.getTargetNodeCode(), null, null);
                    operationExecutor.bindTarget(request.getOperationId(), instance.getId(), null);
                    if (InstanceStatusEnum.TERMINATED.name().equals(instance.getInstanceStatus())) {
                        if (instanceRepository.reopenForJump(instance.getId()) != 1) {
                            throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_STATUS_INVALID,
                                    "terminated instance cannot be reopened for jump");
                        }
                        instance.setInstanceStatus(InstanceStatusEnum.RUNNING.name());
                        instance.setCurrentNodeCodes("[]");
                        instance.setEndedAt(null);
                    }
                    List<HistoryTaskDTO> archivedTasks = taskCancellationService.cancelOpenWork(instance, operator,
                            ActionTypeEnum.JUMP, request.getComment(), request.getOperationId(),
                            readVariables(instance.getVariablesJson()));
                    RuntimeAdvanceResult advanceResult = nodeAdvancer.advanceToNode(instance, definition,
                            request.getTargetNodeCode(), null, null, preparation);
                    ProcessInstanceDTO resultInstance = loadInstanceResult(instance.getId(),
                            advanceResult.getCreatedTasks());
                    TaskActionResult result = new TaskActionResult();
                    result.setOperationId(request.getOperationId());
                    result.setInstance(resultInstance);
                    result.setArchivedTasks(archivedTasks);
                    result.setCreatedTasks(new ArrayList<TaskDTO>(advanceResult.getCreatedTasks()));
                    result.setReplayed(false);
                    writeAudit(instance.getId(), operator, request.getOperationId(), ActionTypeEnum.JUMP,
                            request.getComment(), archivedTasks.size());
                    publishEvent(request.getOperationId(), WorkflowEventTypeEnum.PROCESS_JUMPED, resultInstance,
                            operator, ActionTypeEnum.JUMP, archivedTasks, result.getCreatedTasks());
                    if (advanceResult.isInstanceCompleted()) {
                        publishEvent(request.getOperationId(), WorkflowEventTypeEnum.PROCESS_COMPLETED, resultInstance,
                                operator, ActionTypeEnum.JUMP, archivedTasks, Collections.<TaskDTO>emptyList());
                    }
                    operationExecutor.markSuccess(request.getOperationId(), result);
                    return result;
                }
            });
        } catch (RuntimeValidationException ex) {
            operationExecutor.markDeterministicFailure(request.getOperationId(), ex.getErrorCode());
            throw ex;
        } catch (RuntimeStateException ex) {
            operationExecutor.markDeterministicFailure(request.getOperationId(), ex.getErrorCode());
            throw ex;
        }
    }

    /** {@inheritDoc} */
    @Override
    public ProcessInstanceDTO forceComplete(final ForceCompleteRequest request) {
        final UserContext operator = requestValidator.validateInstanceOperationIdentity(request, request.getInstanceId(),
                request.getOperatorUserId());
        OperationIdempotencyDecision decision = operationExecutor.begin(request, RuntimeOperationTypes.FORCE_COMPLETE,
                operator.getUserId(), request.getInstanceId(), null, LocalDateTime.now());
        if (isSuccessfulReplay(decision)) {
            return operationExecutor.replayResult(decision, ProcessInstanceDTO.class);
        }
        operationExecutor.assertExecutable(decision);
        try {
            return transactionExecutor.execute(new RuntimeTransactionWork<ProcessInstanceDTO>() {
                @Override
                public ProcessInstanceDTO execute() {
                    ProcessInstanceEntity instance = requireRunningInstance(request.getInstanceId());
                    operationExecutor.bindTarget(request.getOperationId(), instance.getId(), null);
                    List<HistoryTaskDTO> archivedTasks = taskCancellationService.cancelOpenWork(instance, operator,
                            ActionTypeEnum.FORCE_COMPLETE, request.getComment(), request.getOperationId(),
                            readVariables(instance.getVariablesJson()));
                    LocalDateTime endedAt = LocalDateTime.now();
                    if (instanceRepository.forceComplete(instance.getId(), endedAt) != 1) {
                        throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_STATUS_INVALID,
                                "process instance cannot be force completed");
                    }
                    instance.setInstanceStatus(InstanceStatusEnum.COMPLETED.name());
                    instance.setCurrentNodeCodes("[]");
                    instance.setEndedAt(endedAt);
                    ProcessInstanceDTO result = RuntimeModelMapper.toDto(instance);
                    result.setCreatedTasks(Collections.<TaskDTO>emptyList());
                    writeAudit(instance.getId(), operator, request.getOperationId(), ActionTypeEnum.FORCE_COMPLETE,
                            request.getComment(), archivedTasks.size());
                    publishEvent(request.getOperationId(), WorkflowEventTypeEnum.PROCESS_COMPLETED, result, operator,
                            ActionTypeEnum.FORCE_COMPLETE, archivedTasks, Collections.<TaskDTO>emptyList());
                    operationExecutor.markSuccess(request.getOperationId(), result);
                    return result;
                }
            });
        } catch (RuntimeValidationException ex) {
            operationExecutor.markDeterministicFailure(request.getOperationId(), ex.getErrorCode());
            throw ex;
        } catch (RuntimeStateException ex) {
            operationExecutor.markDeterministicFailure(request.getOperationId(), ex.getErrorCode());
            throw ex;
        }
    }

    /** C 线负责管理员实例分页查询。 */
    @Override
    public PageResult<ProcessInstanceDTO> queryInstances(AdminInstanceQuery query) {
        throw queryUnsupported("queryInstances");
    }

    /** C 线负责管理员活动任务分页查询。 */
    @Override
    public PageResult<TaskDTO> queryActiveTasks(AdminTaskQuery query) {
        throw queryUnsupported("queryActiveTasks");
    }

    /** C 线负责管理员历史任务分页查询。 */
    @Override
    public PageResult<HistoryTaskDTO> queryHistoryTasks(AdminHistoryTaskQuery query) {
        throw queryUnsupported("queryHistoryTasks");
    }

    /** C 线负责审计日志分页查询。 */
    @Override
    public PageResult<AuditLogDTO> queryAuditLogs(AuditLogQuery query) {
        throw queryUnsupported("queryAuditLogs");
    }

    /** C 线负责回调日志分页查询。 */
    @Override
    public PageResult<CallbackLogDTO> queryCallbackLogs(CallbackLogQuery query) {
        throw queryUnsupported("queryCallbackLogs");
    }

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

    private ProcessInstanceEntity requireJumpableInstance(String instanceId) {
        ProcessInstanceEntity instance = instanceRepository.findById(instanceId);
        if (instance == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_NOT_FOUND, "process instance does not exist");
        }
        if (!InstanceStatusEnum.RUNNING.name().equals(instance.getInstanceStatus())
                && !InstanceStatusEnum.TERMINATED.name().equals(instance.getInstanceStatus())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_STATUS_INVALID,
                    "process instance status does not allow a jump");
        }
        return instance;
    }

    private void assertJumpTarget(ProcessDefinitionDetailDTO definition, String targetNodeCode) {
        if (targetNodeCode == null || targetNodeCode.trim().isEmpty()) {
            throw new RuntimeValidationException(RuntimeErrorCodes.NODE_NOT_FOUND, "targetNodeCode is required");
        }
        DefinitionGraphIndex graph = DefinitionGraphIndex.from(definition);
        com.flowmind.platform.api.dto.ProcessNodeDTO node = graph.getNodesByCode().get(targetNodeCode);
        if (node == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.NODE_NOT_FOUND, "target node does not exist");
        }
        if (NodeTypeEnum.START.equals(node.getNodeType())
                || NodeTypeEnum.PARALLEL_JOIN_GATEWAY.equals(node.getNodeType())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "jump target does not support the requested node type");
        }
    }

    private ProcessInstanceDTO loadInstanceResult(String instanceId, List<TaskDTO> createdTasks) {
        ProcessInstanceEntity persisted = instanceRepository.findById(instanceId);
        if (persisted == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_NOT_FOUND,
                    "process instance disappeared while jumping");
        }
        ProcessInstanceDTO result = RuntimeModelMapper.toDto(persisted);
        result.setCreatedTasks(new ArrayList<TaskDTO>(createdTasks));
        return result;
    }

    private Map<String, Object> readVariables(String variablesJson) {
        try {
            return RuntimeJsonCodec.readObjectMap(variablesJson);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeStateException(RuntimeErrorCodes.DEFINITION_INVALID,
                    "instance variables are malformed");
        }
    }

    private void writeAudit(String instanceId,
                            UserContext operator,
                            String operationId,
                            ActionTypeEnum actionType,
                            String comment,
                            int archivedTaskCount) {
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("comment", comment);
        detail.put("archivedTaskCount", Integer.valueOf(archivedTaskCount));
        if (definitionRepository.insertAuditLog(UUID.randomUUID().toString(), instanceId, operationId,
                OperationTargetTypeEnum.INSTANCE.name(), instanceId, actionType.name(), operator.getUserId(),
                RuntimeJsonCodec.toJson(detail), LocalDateTime.now()) != 1) {
            throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                    "failed to write instance management audit log");
        }
    }

    private void publishEvent(String operationId,
                              WorkflowEventTypeEnum eventType,
                              ProcessInstanceDTO instance,
                              UserContext operator,
                              ActionTypeEnum actionType,
                              List<HistoryTaskDTO> archivedTasks,
                              List<TaskDTO> createdTasks) {
        WorkflowEvent event = new WorkflowEvent();
        event.setEventId(operationId + ":" + eventType.name() + ":" + instance.getInstanceId());
        event.setOperationId(operationId);
        event.setEventType(eventType);
        event.setProcessCode(instance.getProcessCode());
        event.setInstanceId(instance.getInstanceId());
        event.setActionType(actionType);
        event.setOperator(new UserContext(operator.getUserId(), operator.getUserName(), operator.getDepartmentId(),
                operator.getDepartmentName()));
        event.setArchivedTasks(new ArrayList<HistoryTaskDTO>(archivedTasks));
        event.setCreatedTasks(new ArrayList<TaskDTO>(createdTasks));
        event.setVariables(instance.getVariables() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(instance.getVariables()));
        event.setOccurredAt(LocalDateTime.now());
        callbackService.publishCallback(event);
    }

    private boolean isSuccessfulReplay(OperationIdempotencyDecision decision) {
        return decision != null && OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType());
    }

    private UnsupportedOperationException queryUnsupported(String methodName) {
        return new UnsupportedOperationException(methodName + " is owned by C M3 query work");
    }
}
