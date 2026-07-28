package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.UserDTO;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.enums.OperationTargetTypeEnum;
import com.flowmind.platform.api.enums.TaskGroupTypeEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import com.flowmind.platform.api.request.AddSignRequest;
import com.flowmind.platform.api.request.ApproveTaskRequest;
import com.flowmind.platform.api.request.DirectSendRequest;
import com.flowmind.platform.api.request.RejectTaskRequest;
import com.flowmind.platform.api.request.ReturnTaskRequest;
import com.flowmind.platform.api.request.TaskOperationRequest;
import com.flowmind.platform.api.request.TransferTaskRequest;
import com.flowmind.platform.api.request.WithdrawTaskRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.spi.OrganizationProvider;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.core.definition.TaskActionRuleConfigReader;
import com.flowmind.platform.core.definition.TaskActionRuleConfigReader.TaskActionRules;
import com.flowmind.platform.core.task.HistoryArchiveCommand;
import com.flowmind.platform.core.task.HistoryTaskWriter;
import com.flowmind.platform.core.validation.DefinitionGraphIndex;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessHistoryTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessTaskGroupEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessHistoryTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.TaskGroupRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * M5 增强任务动作的内部协调器。
 *
 * <p>该组件不提供新的公共 Service 契约。它把增强动作的幂等租约、任务 CAS、历史、任务创建、
 * 回调和成功结果放在同一个运行时事务中，避免 {@link DefaultProcessRuntimeService} 再次膨胀。</p>
 *
 * @author FlowMind
 * @since 2026-07-27
 */
@Component
public class EnhancedTaskActionCoordinator {

    private static final String ADD_SIGN_PURPOSE = "ADD_SIGN";

    private final ProcessInstanceRepository instanceRepository;
    private final ActiveTaskRepository activeTaskRepository;
    private final ProcessHistoryTaskRepository historyRepository;
    private final TaskGroupRepository taskGroupRepository;
    private final RuntimeDefinitionLoader definitionLoader;
    private final RuntimeRequestValidator requestValidator;
    private final RuntimeOperationExecutor operationExecutor;
    private final RuntimeNodeAdvancer nodeAdvancer;
    private final RuntimeStateValidator stateValidator;
    private final HistoryTaskWriter historyTaskWriter;
    private final RuntimeTransactionExecutor transactionExecutor;
    private final CallbackService callbackService;
    private final OrganizationProvider organizationProvider;
    private final TaskActionRuleConfigReader taskActionRuleConfigReader = new TaskActionRuleConfigReader();
    /** 复用既有审计写入原语；C 线统一 Writer 合入前不复制 SQL。 */
    private final ProcessDefinitionRepository auditRepository;

    public EnhancedTaskActionCoordinator(ProcessInstanceRepository instanceRepository,
                                         ActiveTaskRepository activeTaskRepository,
                                         ProcessHistoryTaskRepository historyRepository,
                                         TaskGroupRepository taskGroupRepository,
                                         RuntimeDefinitionLoader definitionLoader,
                                         RuntimeRequestValidator requestValidator,
                                         RuntimeOperationExecutor operationExecutor,
                                         RuntimeNodeAdvancer nodeAdvancer,
                                         RuntimeStateValidator stateValidator,
                                         HistoryTaskWriter historyTaskWriter,
                                         RuntimeTransactionExecutor transactionExecutor,
                                         CallbackService callbackService,
                                         ObjectProvider<OrganizationProvider> organizationProviderProvider,
                                         ObjectProvider<ProcessDefinitionRepository> auditRepositoryProvider) {
        this.instanceRepository = instanceRepository;
        this.activeTaskRepository = activeTaskRepository;
        this.historyRepository = historyRepository;
        this.taskGroupRepository = taskGroupRepository;
        this.definitionLoader = definitionLoader;
        this.requestValidator = requestValidator;
        this.operationExecutor = operationExecutor;
        this.nodeAdvancer = nodeAdvancer;
        this.stateValidator = stateValidator;
        this.historyTaskWriter = historyTaskWriter;
        this.transactionExecutor = transactionExecutor;
        this.callbackService = callbackService;
        this.organizationProvider = organizationProviderProvider.getIfAvailable();
        this.auditRepository = auditRepositoryProvider.getIfAvailable();
    }

    public TaskActionResult reject(final RejectTaskRequest request) {
        return execute(request, ActionTypeEnum.REJECT, new ActionWork() {
            @Override public TaskActionResult run(EnhancedActionContext context) {
                requireSerial(context.task);
                requireText(request.getTargetNodeCode(), "targetNodeCode");
                requireUserTask(context.definition, request.getTargetNodeCode());
                assertRejectRule(context.definition, context.task.getNodeCode(), request.getTargetNodeCode());
                RuntimeAdvancePreparation preparation = nodeAdvancer.prepareAdvance(context.instance, context.definition,
                        request.getTargetNodeCode(), null, null);
                complete(context.task, request);
                Map<String, Object> metadata = metadata(context, request.getTargetNodeCode());
                ProcessHistoryTaskEntity history = archive(context, ActionTypeEnum.REJECT, request, metadata);
                RuntimeAdvanceResult advance = nodeAdvancer.advanceToNode(context.instance, context.definition,
                        request.getTargetNodeCode(), null, null, preparation);
                metadata.put("createdTaskIds", taskIds(advance.getCreatedTasks()));
                history.setExtraJson(RuntimeJsonCodec.toJson(metadata));
                if (historyRepository.updateExtraJson(history.getId(), history.getExtraJson()) != 1) {
                    throw state(RuntimeErrorCodes.HISTORY_ARCHIVE_INVALID, "reject history relation cannot be persisted");
                }
                return result(context, request, ActionTypeEnum.REJECT, Collections.singletonList(history),
                        advance.getCreatedTasks(), Collections.<TaskDTO>emptyList(), WorkflowEventTypeEnum.PROCESS_REJECTED);
            }
        });
    }

    public TaskActionResult returnToStarter(final ReturnTaskRequest request) {
        return execute(request, ActionTypeEnum.RETURN, new ActionWork() {
            @Override public TaskActionResult run(EnhancedActionContext context) {
                requireSerial(context.task);
                ProcessHistoryTaskEntity target = findStarterHistory(context);
                requireUserTask(context.definition, target.getNodeCode());
                RuntimeAdvancePreparation preparation = nodeAdvancer.prepareAdvance(context.instance, context.definition,
                        target.getNodeCode(), null, null);
                complete(context.task, request);
                Map<String, Object> metadata = metadata(context, target.getNodeCode());
                metadata.put("relatedHistoryTaskId", target.getId());
                ProcessHistoryTaskEntity archived = archive(context, ActionTypeEnum.RETURN, request, metadata);
                RuntimeAdvanceResult advance = nodeAdvancer.advanceToNode(context.instance, context.definition,
                        target.getNodeCode(), null, null, preparation);
                return result(context, request, ActionTypeEnum.RETURN, Collections.singletonList(archived),
                        advance.getCreatedTasks(), Collections.<TaskDTO>emptyList(), WorkflowEventTypeEnum.PROCESS_RETURNED);
            }
        });
    }

    public TaskActionResult withdraw(final WithdrawTaskRequest request) {
        return execute(request, ActionTypeEnum.WITHDRAW, new ActionWork() {
            @Override public TaskActionResult run(EnhancedActionContext context) {
                requireSerial(context.task);
                if (activeTaskRepository.countOpenByInstanceId(context.instance.getId()) != 1L) {
                    throw state(RuntimeErrorCodes.GROUPED_TASK_ACTION_NOT_SUPPORTED,
                            "withdraw requires exactly one open serial task");
                }
                ProcessHistoryTaskEntity previous = findPreviousNodeHistory(context);
                if (!context.operator.getUserId().equals(previous.getAssigneeUserId())) {
                    throw validation(RuntimeErrorCodes.WITHDRAW_PERMISSION_DENIED,
                            "only the previous task handler can withdraw");
                }
                requireUserTask(context.definition, previous.getNodeCode());
                RuntimeAdvancePreparation preparation = nodeAdvancer.prepareAdvance(context.instance, context.definition,
                        previous.getNodeCode(), null, null);
                cancel(context.task, request);
                Map<String, Object> metadata = metadata(context, previous.getNodeCode());
                metadata.put("relatedHistoryTaskId", previous.getId());
                ProcessHistoryTaskEntity archived = archive(context, ActionTypeEnum.WITHDRAW, request, metadata);
                RuntimeAdvanceResult advance = nodeAdvancer.advanceToNode(context.instance, context.definition,
                        previous.getNodeCode(), null, null, preparation);
                return result(context, request, ActionTypeEnum.WITHDRAW, Collections.singletonList(archived),
                        advance.getCreatedTasks(), Collections.<TaskDTO>emptyList(), WorkflowEventTypeEnum.PROCESS_WITHDRAWN);
            }
        });
    }

    public TaskActionResult directSend(final DirectSendRequest request) {
        return execute(request, ActionTypeEnum.DIRECT_SEND, new ActionWork() {
            @Override public TaskActionResult run(EnhancedActionContext context) {
                requireSerial(context.task);
                requireText(request.getTargetNodeCode(), "targetNodeCode");
                ProcessHistoryTaskEntity source = findRejectSource(context);
                String sourceNodeCode = readText(source.getExtraJson(), "sourceNodeCode");
                if (!request.getTargetNodeCode().equals(sourceNodeCode)) {
                    throw validation(RuntimeErrorCodes.DIRECT_SEND_SOURCE_NOT_FOUND,
                            "direct send target must be the reject source node");
                }
                assertDirectSendRule(context.definition, context.task.getNodeCode());
                requireUserTask(context.definition, sourceNodeCode);
                RuntimeAdvancePreparation preparation = nodeAdvancer.prepareAdvance(context.instance, context.definition,
                        sourceNodeCode, null, null);
                complete(context.task, request);
                Map<String, Object> metadata = metadata(context, sourceNodeCode);
                metadata.put("relatedHistoryTaskId", source.getId());
                ProcessHistoryTaskEntity archived = archive(context, ActionTypeEnum.DIRECT_SEND, request, metadata);
                RuntimeAdvanceResult advance = nodeAdvancer.advanceToNode(context.instance, context.definition,
                        sourceNodeCode, null, null, preparation);
                return result(context, request, ActionTypeEnum.DIRECT_SEND, Collections.singletonList(archived),
                        advance.getCreatedTasks(), Collections.<TaskDTO>emptyList(), WorkflowEventTypeEnum.PROCESS_DIRECT_SENT);
            }
        });
    }

    public TaskActionResult transfer(final TransferTaskRequest request) {
        return execute(request, ActionTypeEnum.TRANSFER, new ActionWork() {
            @Override public TaskActionResult run(EnhancedActionContext context) {
                requireText(request.getTargetUserId(), "targetUserId");
                if (request.getTargetUserId().equals(context.task.getAssigneeUserId())) {
                    throw validation(RuntimeErrorCodes.INVALID_ACTION, "cannot transfer task to its current assignee");
                }
                UserDTO target = requireUser(request.getTargetUserId());
                if (activeTaskRepository.transfer(context.task.getId(), request.getExpectedTaskVersion().longValue(),
                        target.getUserId(), target.getUserName()) != 1) {
                    throw state(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED, "task was modified while transferring");
                }
                String previousAssigneeUserId = context.task.getAssigneeUserId();
                context.task.setAssigneeUserId(target.getUserId());
                context.task.setAssigneeUserName(target.getUserName());
                context.task.setLockVersion(Long.valueOf(context.task.getLockVersion().longValue() + 1L));
                Map<String, Object> metadata = metadata(context, context.task.getNodeCode());
                metadata.put("fromAssigneeUserId", previousAssigneeUserId);
                metadata.put("targetUserIds", Collections.singletonList(target.getUserId()));
                ProcessHistoryTaskEntity archived = archive(context, ActionTypeEnum.TRANSFER, request, metadata);
                return result(context, request, ActionTypeEnum.TRANSFER, Collections.singletonList(archived),
                        Collections.<TaskDTO>emptyList(), Collections.singletonList(RuntimeModelMapper.toDto(context.task,
                                null, null)), WorkflowEventTypeEnum.TASK_TRANSFERRED);
            }
        });
    }

    public TaskActionResult addSign(final AddSignRequest request) {
        return execute(request, ActionTypeEnum.ADD_SIGN, new ActionWork() {
            @Override public TaskActionResult run(EnhancedActionContext context) {
                requireSerial(context.task);
                List<UserDTO> users = resolveAddSignUsers(request.getAddSignUserIds(), context.operator.getUserId());
                cancel(context.task, request);
                ProcessTaskGroupEntity group = createAddSignGroup(context, request, users.size());
                List<TaskDTO> created = new ArrayList<TaskDTO>();
                for (UserDTO user : users) {
                    ProcessActiveTaskEntity temporary = createTemporaryTask(context, group, user);
                    created.add(RuntimeModelMapper.toDto(temporary, null, null));
                }
                Map<String, Object> metadata = metadata(context, context.task.getNodeCode());
                metadata.put("addSignGroupId", group.getId());
                metadata.put("targetUserIds", userIds(users));
                ProcessHistoryTaskEntity archived = archive(context, ActionTypeEnum.ADD_SIGN, request, metadata);
                return result(context, request, ActionTypeEnum.ADD_SIGN, Collections.singletonList(archived), created,
                        Collections.<TaskDTO>emptyList(), WorkflowEventTypeEnum.TASK_ADDED_SIGN);
            }
        });
    }

    /** 判断审批请求是否命中 M5 加签临时任务；读取失败按普通审批处理。 */
    public boolean isAddSignTask(String taskId) {
        ProcessActiveTaskEntity task = activeTaskRepository.findById(taskId);
        if (task == null || isBlank(task.getTaskGroupId())) {
            return false;
        }
        ProcessTaskGroupEntity group = taskGroupRepository.findById(task.getTaskGroupId());
        return group != null && isAddSignGroup(group);
    }

    /** 加签临时任务的审批收口，不推进流程出线。 */
    public TaskActionResult approveAddSign(final ApproveTaskRequest request) {
        return execute(request, ActionTypeEnum.APPROVE, new ActionWork() {
            @Override public TaskActionResult run(EnhancedActionContext context) {
                ProcessTaskGroupEntity group = taskGroupRepository.findById(context.task.getTaskGroupId());
                if (group == null || !isAddSignGroup(group)) {
                    throw state(RuntimeErrorCodes.ADD_SIGN_CONTEXT_INVALID, "add-sign task group is invalid");
                }
                complete(context.task, request);
                ProcessHistoryTaskEntity archived = archive(context, ActionTypeEnum.APPROVE, request,
                        metadata(context, context.task.getNodeCode()));
                if (taskGroupRepository.incrementCompletedCount(group.getId(), group.getLockVersion().longValue()) != 1) {
                    throw state(RuntimeErrorCodes.TASK_GROUP_CONCURRENT_MODIFIED, "add-sign group was modified");
                }
                ProcessTaskGroupEntity updated = taskGroupRepository.findById(group.getId());
                List<TaskDTO> created = updated != null && "COMPLETED".equals(updated.getGroupStatus())
                        ? Collections.singletonList(RuntimeModelMapper.toDto(restoreSourceTask(context, updated), null, null))
                        : Collections.<TaskDTO>emptyList();
                return result(context, request, ActionTypeEnum.APPROVE, Collections.singletonList(archived), created,
                        Collections.<TaskDTO>emptyList(), WorkflowEventTypeEnum.TASK_COMPLETED);
            }
        });
    }

    private TaskActionResult execute(TaskOperationRequest request, ActionTypeEnum action, ActionWork work) {
        final UserContext operator = requestValidator.validateTaskIdentity(request);
        OperationIdempotencyDecision decision = operationExecutor.begin(request, operationType(action),
                operator.getUserId(), null, request.getTaskId(), LocalDateTime.now());
        if (decision != null && OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType())) {
            return operationExecutor.replayTaskAction(decision);
        }
        operationExecutor.assertExecutable(decision);
        try {
            return inTransaction(new RuntimeTransactionWork<TaskActionResult>() {
                @Override public TaskActionResult execute() {
                    EnhancedActionContext context = loadContext(request, operator, action);
                    TaskActionResult result = work.run(context);
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

    private EnhancedActionContext loadContext(TaskOperationRequest request, UserContext operator, ActionTypeEnum action) {
        ProcessActiveTaskEntity task;
        ProcessInstanceEntity instance;
        if (stateValidator != null) {
            RuntimeTaskContext validated = stateValidator.validateTaskAction(request.getTaskId(),
                    request.getExpectedTaskVersion(), action, operator);
            task = validated.getTask();
            instance = validated.getInstance();
        } else {
            task = activeTaskRepository.findById(request.getTaskId());
            instance = task == null ? null : instanceRepository.findById(task.getInstanceId());
        }
        if (ActionTypeEnum.WITHDRAW.equals(action)) {
            validateWithdrawContext(request, instance, task);
        } else {
            requestValidator.validateTaskAction(request, instance, task, operator);
        }
        operationExecutor.bindTarget(request.getOperationId(), instance.getId(), task.getId());
        return new EnhancedActionContext(instance, task, definitionLoader.loadForInstance(instance), operator);
    }

    /** 撤回授权以历史上一办理人为准，不能要求操作者拥有当前下游待办的办理权限。 */
    private void validateWithdrawContext(TaskOperationRequest request, ProcessInstanceEntity instance,
                                         ProcessActiveTaskEntity task) {
        if (task == null || !request.getTaskId().equals(task.getId())) {
            throw state(RuntimeErrorCodes.TASK_NOT_FOUND, "active task does not exist");
        }
        if (instance == null || !task.getInstanceId().equals(instance.getId())) {
            throw state(RuntimeErrorCodes.INSTANCE_NOT_FOUND, "task instance does not exist");
        }
        if (!"RUNNING".equals(instance.getInstanceStatus())) {
            throw state(RuntimeErrorCodes.INSTANCE_STATUS_INVALID, "process instance is not running");
        }
        if (!TaskStatusEnum.ACTIVE.name().equals(task.getTaskStatus())
                && !TaskStatusEnum.CLAIMED.name().equals(task.getTaskStatus())) {
            throw state(RuntimeErrorCodes.TASK_NOT_ACTIVE, "task is not active");
        }
        if (task.getLockVersion() == null || !task.getLockVersion().equals(request.getExpectedTaskVersion())) {
            throw state(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED, "expectedTaskVersion does not match active task");
        }
    }

    private ProcessHistoryTaskEntity archive(EnhancedActionContext context, ActionTypeEnum action,
                                             TaskOperationRequest request, Map<String, Object> metadata) {
        HistoryArchiveCommand command = new HistoryArchiveCommand();
        command.setInstance(context.instance);
        command.setTask(context.task);
        command.setOperator(context.operator);
        command.setActionType(action);
        command.setOperationId(request.getOperationId());
        command.setComment(request.getComment());
        command.setVariablesSnapshot(readVariables(context.instance));
        command.setCompletedAt(LocalDateTime.now());
        command.setExtraJson(RuntimeJsonCodec.toJson(metadata));
        return historyTaskWriter.archive(command);
    }

    private TaskActionResult result(EnhancedActionContext context, TaskOperationRequest request, ActionTypeEnum action,
                                    List<ProcessHistoryTaskEntity> histories, List<TaskDTO> created,
                                    List<TaskDTO> updated, WorkflowEventTypeEnum eventType) {
        List<com.flowmind.platform.api.dto.HistoryTaskDTO> archived = new ArrayList<com.flowmind.platform.api.dto.HistoryTaskDTO>();
        for (ProcessHistoryTaskEntity history : histories) {
            archived.add(RuntimeModelMapper.toDto(history));
        }
        TaskActionResult result = new TaskActionResult();
        result.setOperationId(request.getOperationId());
        result.setArchivedTasks(archived);
        result.setCreatedTasks(new ArrayList<TaskDTO>(created));
        result.setUpdatedTasks(new ArrayList<TaskDTO>(updated));
        ProcessInstanceEntity persisted = instanceRepository.findById(context.instance.getId());
        ProcessInstanceDTO instance = RuntimeModelMapper.toDto(persisted == null ? context.instance : persisted);
        instance.setCreatedTasks(new ArrayList<TaskDTO>(created));
        result.setInstance(instance);
        writeTaskAudit(context, request, action, histories);
        publish(request.getOperationId(), eventType, context.task.getId(), action, result, context.operator);
        for (TaskDTO task : created) {
            publish(request.getOperationId(), WorkflowEventTypeEnum.TASK_CREATED, task.getTaskId(), action,
                    result, context.operator);
        }
        return result;
    }

    private void writeTaskAudit(EnhancedActionContext context, TaskOperationRequest request, ActionTypeEnum action,
                                List<ProcessHistoryTaskEntity> histories) {
        if (auditRepository == null) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("schemaVersion", Integer.valueOf(1));
        detail.put("comment", request.getComment());
        detail.put("historyTaskIds", historyIds(histories));
        if (auditRepository.insertAuditLog(UUID.randomUUID().toString(), context.instance.getId(), request.getOperationId(),
                OperationTargetTypeEnum.TASK.name(), context.task.getId(), action.name(), context.operator.getUserId(),
                RuntimeJsonCodec.toJson(detail), LocalDateTime.now()) != 1) {
            throw state(RuntimeErrorCodes.INVALID_ACTION, "failed to write enhanced task audit log");
        }
    }

    private void publish(String operationId, WorkflowEventTypeEnum eventType, String targetId, ActionTypeEnum action,
                         TaskActionResult result, UserContext operator) {
        if (callbackService == null) {
            throw state(RuntimeErrorCodes.INVALID_ACTION, "callback service is unavailable");
        }
        com.flowmind.platform.api.dto.WorkflowEvent event = new com.flowmind.platform.api.dto.WorkflowEvent();
        event.setEventId(operationId + ":" + eventType.name() + ":" + targetId);
        event.setOperationId(operationId);
        event.setEventType(eventType);
        event.setProcessCode(result.getInstance().getProcessCode());
        event.setInstanceId(result.getInstance().getInstanceId());
        event.setActionType(action);
        event.setOperator(operator);
        event.setArchivedTasks(result.getArchivedTasks());
        event.setCreatedTasks(result.getCreatedTasks());
        event.setVariables(result.getInstance().getVariables());
        event.setOccurredAt(LocalDateTime.now());
        callbackService.publishCallback(event);
    }

    private void complete(ProcessActiveTaskEntity task, TaskOperationRequest request) {
        if (activeTaskRepository.complete(task.getId(), request.getExpectedTaskVersion().longValue()) != 1) {
            throw state(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED, "active task was modified");
        }
    }

    private void cancel(ProcessActiveTaskEntity task, TaskOperationRequest request) {
        if (activeTaskRepository.cancel(task.getId(), request.getExpectedTaskVersion().longValue()) != 1) {
            throw state(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED, "active task was modified");
        }
    }

    private ProcessHistoryTaskEntity findStarterHistory(EnhancedActionContext context) {
        for (ProcessHistoryTaskEntity history : historyRepository.findLatestByInstanceAndActions(context.instance.getId(),
                ActionTypeEnum.SEND.name())) {
            ProcessNodeDTO node = DefinitionGraphIndex.from(context.definition).getNodesByCode().get(history.getNodeCode());
            if (node != null && com.flowmind.platform.api.enums.ApproverRuleTypeEnum.STARTER.equals(node.getApproverRuleType())) {
                return history;
            }
        }
        throw state(RuntimeErrorCodes.WITHDRAW_HISTORY_NOT_FOUND, "starter history was not found");
    }

    private ProcessHistoryTaskEntity findPreviousNodeHistory(EnhancedActionContext context) {
        for (ProcessHistoryTaskEntity history : historyRepository.findLatestByInstanceAndActions(context.instance.getId(),
                ActionTypeEnum.SEND.name(), ActionTypeEnum.APPROVE.name(), ActionTypeEnum.REJECT.name(),
                ActionTypeEnum.RETURN.name(), ActionTypeEnum.DIRECT_SEND.name())) {
            if (!context.task.getId().equals(history.getActiveTaskId())) {
                return history;
            }
        }
        throw state(RuntimeErrorCodes.WITHDRAW_HISTORY_NOT_FOUND, "previous completed node history was not found");
    }

    private ProcessHistoryTaskEntity findRejectSource(EnhancedActionContext context) {
        for (ProcessHistoryTaskEntity history : historyRepository.findLatestByInstanceAndActions(context.instance.getId(),
                ActionTypeEnum.REJECT.name())) {
            Map<String, Object> metadata = readObject(history.getExtraJson());
            Object schemaVersion = metadata.get("schemaVersion");
            if (schemaVersion instanceof Number && ((Number) schemaVersion).intValue() == 1
                    && readStringList(metadata.get("createdTaskIds")).contains(context.task.getId())) {
                return history;
            }
        }
        throw state(RuntimeErrorCodes.DIRECT_SEND_SOURCE_NOT_FOUND, "reject source was not found");
    }

    private void assertRejectRule(ProcessDefinitionDetailDTO definition, String sourceNodeCode, String targetNodeCode) {
        TaskActionRules rules = actionRules(definition, sourceNodeCode);
        if (!rules.isRejectEnabled() || !rules.getRejectTargetNodeCodes().contains(targetNodeCode)) {
            throw validation(RuntimeErrorCodes.REJECT_TARGET_NOT_ALLOWED, "reject target is not allowed");
        }
    }

    private void assertDirectSendRule(ProcessDefinitionDetailDTO definition, String sourceNodeCode) {
        TaskActionRules rules = actionRules(definition, sourceNodeCode);
        if (!rules.isDirectSendEnabled()
                || !TaskActionRuleConfigReader.TARGET_MODE_REJECT_SOURCE.equals(rules.getDirectSendTargetMode())) {
            throw validation(RuntimeErrorCodes.DIRECT_SEND_SOURCE_NOT_FOUND, "direct send is not enabled");
        }
    }

    private TaskActionRules actionRules(ProcessDefinitionDetailDTO definition, String nodeCode) {
        ProcessNodeDTO node = requireUserTask(definition, nodeCode);
        try {
            return taskActionRuleConfigReader.read(node.getListenerConfig());
        } catch (IllegalArgumentException ex) {
            throw state(RuntimeErrorCodes.NODE_CONFIG_INVALID, "task action rules are malformed");
        }
    }

    private ProcessTaskGroupEntity createAddSignGroup(EnhancedActionContext context, AddSignRequest request, int count) {
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("schemaVersion", Integer.valueOf(1));
        snapshot.put("purpose", ADD_SIGN_PURPOSE);
        snapshot.put("sourceTaskId", context.task.getId());
        snapshot.put("sourceNodeCode", context.task.getNodeCode());
        snapshot.put("sourceCandidateUserIds", context.task.getCandidateUserIds());
        snapshot.put("sourceAssigneeUserId", context.task.getAssigneeUserId());
        snapshot.put("sourceAssigneeUserName", context.task.getAssigneeUserName());
        snapshot.put("sourceOperationId", request.getOperationId());
        ProcessTaskGroupEntity group = new ProcessTaskGroupEntity();
        group.setId(UUID.randomUUID().toString());
        group.setInstanceId(context.instance.getId());
        group.setNodeCode(context.task.getNodeCode());
        group.setGroupType(TaskGroupTypeEnum.COUNTERSIGN.name());
        group.setTotalCount(Integer.valueOf(count));
        group.setCompletedCount(Integer.valueOf(0));
        group.setBranchStateJson(RuntimeJsonCodec.toJson(snapshot));
        group.setGroupStatus("ACTIVE");
        group.setLockVersion(Long.valueOf(0));
        group.setCreatedAt(LocalDateTime.now());
        if (taskGroupRepository.insert(group) != 1) {
            throw state(RuntimeErrorCodes.ADD_SIGN_CONTEXT_INVALID, "failed to create add-sign task group");
        }
        return group;
    }

    private ProcessActiveTaskEntity createTemporaryTask(EnhancedActionContext context, ProcessTaskGroupEntity group,
                                                        UserDTO user) {
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId(UUID.randomUUID().toString());
        task.setInstanceId(context.instance.getId());
        task.setDefinitionId(context.instance.getDefinitionId());
        task.setNodeCode(context.task.getNodeCode());
        task.setCandidateUserIds(RuntimeJsonCodec.toJson(Collections.singletonList(user.getUserId())));
        task.setAssigneeUserId(user.getUserId());
        task.setAssigneeUserName(user.getUserName());
        task.setTaskStatus("ACTIVE");
        task.setTaskGroupId(group.getId());
        task.setLockVersion(Long.valueOf(0));
        task.setCreatedAt(LocalDateTime.now());
        if (activeTaskRepository.insert(task) != 1) {
            throw state(RuntimeErrorCodes.ADD_SIGN_CONTEXT_INVALID, "failed to create add-sign task");
        }
        return task;
    }

    private ProcessActiveTaskEntity restoreSourceTask(EnhancedActionContext context, ProcessTaskGroupEntity group) {
        Map<String, Object> snapshot = readObject(group.getBranchStateJson());
        if (!ADD_SIGN_PURPOSE.equals(snapshot.get("purpose")) || !context.instance.getId().equals(group.getInstanceId())) {
            throw state(RuntimeErrorCodes.ADD_SIGN_CONTEXT_INVALID, "add-sign group source snapshot is invalid");
        }
        ProcessActiveTaskEntity task = new ProcessActiveTaskEntity();
        task.setId(UUID.randomUUID().toString());
        task.setInstanceId(context.instance.getId());
        task.setDefinitionId(context.instance.getDefinitionId());
        task.setNodeCode(readText(snapshot, "sourceNodeCode"));
        task.setCandidateUserIds(readText(snapshot, "sourceCandidateUserIds"));
        task.setAssigneeUserId(optionalText(snapshot, "sourceAssigneeUserId"));
        task.setAssigneeUserName(optionalText(snapshot, "sourceAssigneeUserName"));
        task.setTaskStatus("ACTIVE");
        task.setLockVersion(Long.valueOf(0));
        task.setCreatedAt(LocalDateTime.now());
        if (activeTaskRepository.insert(task) != 1) {
            throw state(RuntimeErrorCodes.ADD_SIGN_CONTEXT_INVALID, "failed to restore source task");
        }
        return task;
    }

    private List<UserDTO> resolveAddSignUsers(List<String> userIds, String operatorUserId) {
        if (userIds == null || userIds.isEmpty()) {
            throw validation(RuntimeErrorCodes.INVALID_ACTION, "addSignUserIds are required");
        }
        Set<String> unique = new LinkedHashSet<String>();
        List<UserDTO> users = new ArrayList<UserDTO>();
        for (String userId : userIds) {
            requireText(userId, "addSignUserId");
            if (operatorUserId.equals(userId) || !unique.add(userId)) {
                throw validation(RuntimeErrorCodes.INVALID_ACTION, "add-sign users must be unique and exclude operator");
            }
            users.add(requireUser(userId));
        }
        return users;
    }

    private UserDTO requireUser(String userId) {
        Optional<UserDTO> user = organizationProvider == null ? Optional.<UserDTO>empty() : organizationProvider.findUser(userId);
        if (!user.isPresent() || Boolean.FALSE.equals(user.get().getActive())) {
            throw validation(RuntimeErrorCodes.TARGET_USER_NOT_FOUND, "target user does not exist or is inactive");
        }
        return user.get();
    }

    private Map<String, Object> metadata(EnhancedActionContext context, String targetNodeCode) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("schemaVersion", Integer.valueOf(1));
        metadata.put("sourceNodeCode", context.task.getNodeCode());
        metadata.put("targetNodeCode", targetNodeCode);
        metadata.put("sourceTaskId", context.task.getId());
        metadata.put("operatorUserId", context.operator.getUserId());
        return metadata;
    }

    private void requireSerial(ProcessActiveTaskEntity task) {
        if (!isBlank(task.getTaskGroupId()) || !isBlank(task.getBranchKey())) {
            throw validation(RuntimeErrorCodes.GROUPED_TASK_ACTION_NOT_SUPPORTED,
                    "enhanced routing action does not support grouped tasks");
        }
    }

    private boolean isAddSignGroup(ProcessTaskGroupEntity group) {
        return TaskGroupTypeEnum.COUNTERSIGN.name().equals(group.getGroupType())
                && ADD_SIGN_PURPOSE.equals(readObject(group.getBranchStateJson()).get("purpose"));
    }

    private ProcessNodeDTO requireUserTask(ProcessDefinitionDetailDTO definition, String nodeCode) {
        ProcessNodeDTO node = DefinitionGraphIndex.from(definition).getNodesByCode().get(nodeCode);
        if (node == null || !NodeTypeEnum.USER_TASK.equals(node.getNodeType())) {
            throw state(RuntimeErrorCodes.NODE_NOT_FOUND, "target node is not a user task: " + nodeCode);
        }
        return node;
    }

    private String operationType(ActionTypeEnum action) {
        switch (action) {
            case REJECT: return RuntimeOperationTypes.REJECT;
            case RETURN: return RuntimeOperationTypes.RETURN;
            case WITHDRAW: return RuntimeOperationTypes.WITHDRAW;
            case DIRECT_SEND: return RuntimeOperationTypes.DIRECT_SEND;
            case TRANSFER: return RuntimeOperationTypes.TRANSFER;
            case ADD_SIGN: return RuntimeOperationTypes.ADD_SIGN;
            case APPROVE: return RuntimeOperationTypes.APPROVE_TASK;
            default: throw new IllegalArgumentException("unsupported enhanced action: " + action);
        }
    }

    private <T> T inTransaction(RuntimeTransactionWork<T> work) {
        return transactionExecutor == null ? work.execute() : transactionExecutor.execute(work);
    }

    private Map<String, Object> readVariables(ProcessInstanceEntity instance) {
        return readObject(instance.getVariablesJson());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObject(String json) {
        try {
            return json == null || json.trim().isEmpty() ? new LinkedHashMap<String, Object>()
                    : RuntimeJsonCodec.readObjectMap(json);
        } catch (IllegalArgumentException ex) {
            throw state(RuntimeErrorCodes.ADD_SIGN_CONTEXT_INVALID, "runtime JSON is malformed");
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<String>();
        for (Object item : (List<Object>) value) {
            if (item instanceof String) {
                values.add((String) item);
            }
        }
        return values;
    }

    private List<String> readStringList(String json, String key) {
        return readStringList(readObject(json).get(key));
    }

    private String readText(String json, String key) { return readText(readObject(json), key); }
    private String readText(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String) || isBlank((String) value)) {
            throw state(RuntimeErrorCodes.ADD_SIGN_CONTEXT_INVALID, "required context field is missing: " + key);
        }
        return (String) value;
    }
    private String optionalText(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String && !isBlank((String) value) ? (String) value : null;
    }
    private List<String> taskIds(List<TaskDTO> tasks) { List<String> ids = new ArrayList<String>(); for (TaskDTO task : tasks) { ids.add(task.getTaskId()); } return ids; }
    private List<String> userIds(List<UserDTO> users) { List<String> ids = new ArrayList<String>(); for (UserDTO user : users) { ids.add(user.getUserId()); } return ids; }
    private List<String> historyIds(List<ProcessHistoryTaskEntity> histories) { List<String> ids = new ArrayList<String>(); for (ProcessHistoryTaskEntity history : histories) { ids.add(history.getId()); } return ids; }
    private void requireText(String value, String name) { if (isBlank(value)) { throw validation(RuntimeErrorCodes.INVALID_ACTION, name + " is required"); } }
    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private static RuntimeValidationException validation(String code, String message) { return new RuntimeValidationException(code, message); }
    private static RuntimeStateException state(String code, String message) { return new RuntimeStateException(code, message); }

    private interface ActionWork { TaskActionResult run(EnhancedActionContext context); }
    private static final class EnhancedActionContext {
        private final ProcessInstanceEntity instance; private final ProcessActiveTaskEntity task;
        private final ProcessDefinitionDetailDTO definition; private final UserContext operator;
        private EnhancedActionContext(ProcessInstanceEntity instance, ProcessActiveTaskEntity task,
                                      ProcessDefinitionDetailDTO definition, UserContext operator) {
            this.instance = instance; this.task = task; this.definition = definition; this.operator = operator;
        }
    }
}
