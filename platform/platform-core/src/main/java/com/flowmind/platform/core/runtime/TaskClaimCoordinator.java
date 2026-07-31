package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.DelegateRelationDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskActionResult;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.OperationTargetTypeEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import com.flowmind.platform.api.request.ClaimTaskRequest;
import com.flowmind.platform.api.request.TaskOperationRequest;
import com.flowmind.platform.api.request.UnclaimTaskRequest;
import com.flowmind.platform.api.service.CallbackService;
import com.flowmind.platform.api.spi.DelegateProvider;
import com.flowmind.platform.core.audit.AuditLogCommand;
import com.flowmind.platform.core.audit.AuditLogWriter;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Coordinates M5 claim and unclaim runtime actions. */
@Component
public class TaskClaimCoordinator {

    private final ProcessInstanceRepository instanceRepository;
    private final ActiveTaskRepository activeTaskRepository;
    private final RuntimeRequestValidator requestValidator;
    private final RuntimeOperationExecutor operationExecutor;
    private final RuntimeTransactionExecutor transactionExecutor;
    private final AuditLogWriter auditLogWriter;
    private final CallbackService callbackService;
    private final DelegateProvider delegateProvider;

    public TaskClaimCoordinator(ProcessInstanceRepository instanceRepository,
                                ActiveTaskRepository activeTaskRepository,
                                RuntimeRequestValidator requestValidator,
                                RuntimeOperationExecutor operationExecutor,
                                RuntimeTransactionExecutor transactionExecutor,
                                AuditLogWriter auditLogWriter,
                                CallbackService callbackService,
                                @Nullable DelegateProvider delegateProvider) {
        this.instanceRepository = instanceRepository;
        this.activeTaskRepository = activeTaskRepository;
        this.requestValidator = requestValidator;
        this.operationExecutor = operationExecutor;
        this.transactionExecutor = transactionExecutor;
        this.auditLogWriter = auditLogWriter;
        this.callbackService = callbackService;
        this.delegateProvider = delegateProvider;
    }

    public TaskActionResult claim(final ClaimTaskRequest request) {
        return execute(request, ActionTypeEnum.CLAIM, RuntimeOperationTypes.CLAIM, WorkflowEventTypeEnum.TASK_CLAIMED);
    }

    public TaskActionResult unclaim(final UnclaimTaskRequest request) {
        return execute(request, ActionTypeEnum.UNCLAIM, RuntimeOperationTypes.UNCLAIM,
                WorkflowEventTypeEnum.TASK_UNCLAIMED);
    }

    private TaskActionResult execute(final TaskOperationRequest request,
                                     final ActionTypeEnum action,
                                     final String operationType,
                                     final WorkflowEventTypeEnum eventType) {
        final UserContext operator = requestValidator.validateTaskIdentity(request); //验证用户
        OperationIdempotencyDecision decision = operationExecutor.begin(request, operationType,
                operator.getUserId(), null, request.getTaskId(), LocalDateTime.now()); //幂等验证
        if (decision != null && OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType())) {
            return operationExecutor.replayTaskAction(decision); //满足幂等，返回结果
        }
        operationExecutor.assertExecutable(decision);
        try { //事务执行逻辑：
            return transactionExecutor.execute(new RuntimeTransactionWork<TaskActionResult>() {
                @Override
                public TaskActionResult execute() {
                    ProcessActiveTaskEntity task = requireTask(request);
                    ProcessInstanceEntity instance = requireRunningInstance(task);
                    if (ActionTypeEnum.CLAIM.equals(action)) { //认领任务
                        validateClaim(task, operator);
                        if (activeTaskRepository.claim(task.getId(), request.getExpectedTaskVersion().longValue(),
                                operator.getUserId(), operator.getUserName()) != 1) {
                            throw new RuntimeStateException(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED,
                                    "task was modified while claiming");
                        }
                    } else {
                        validateUnclaim(task, operator);
                        if (activeTaskRepository.unclaim(task.getId(), request.getExpectedTaskVersion().longValue()) != 1) {
                            throw new RuntimeStateException(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED,
                                    "task was modified while unclaiming");
                        }
                    }
                    operationExecutor.bindTarget(request.getOperationId(), instance.getId(), task.getId());
                    ProcessActiveTaskEntity updated = activeTaskRepository.findById(task.getId());
                    TaskActionResult result = result(instance, request, action, operator, updated, eventType);
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

    private ProcessActiveTaskEntity requireTask(TaskOperationRequest request) {
        ProcessActiveTaskEntity task = activeTaskRepository.findById(request.getTaskId());
        if (task == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.TASK_NOT_FOUND, "active task does not exist");
        }
        if (task.getLockVersion() == null || !task.getLockVersion().equals(request.getExpectedTaskVersion())) {
            throw new RuntimeStateException(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED,
                    "expectedTaskVersion does not match active task");
        }
        return task;
    }

    private ProcessInstanceEntity requireRunningInstance(ProcessActiveTaskEntity task) {
        ProcessInstanceEntity instance = instanceRepository.findById(task.getInstanceId());
        if (instance == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_NOT_FOUND, "task instance does not exist");
        }
        if (!"RUNNING".equals(instance.getInstanceStatus())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_STATUS_INVALID,
                    "process instance is not running");
        }
        return instance;
    }

    private void validateClaim(ProcessActiveTaskEntity task, UserContext operator) {
        if (!TaskStatusEnum.ACTIVE.name().equals(task.getTaskStatus())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_ALREADY_CLAIMED,
                    "task is not claimable");
        }
        if (hasText(task.getAssigneeUserId()) && !operator.getUserId().equals(task.getAssigneeUserId())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_CLAIM_PERMISSION_DENIED,
                    "task already has another assignee");
        }
        if (activeTaskRepository.hasClaimedSiblingInActiveOrSignGroup(task.getId(), task.getTaskGroupId())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_ALREADY_CLAIMED,
                    "or-sign task group already has a claimed task");
        }
        List<String> candidates = RuntimeJsonCodec.readStringList(task.getCandidateUserIds());
        List<String> allowedPrincipals = claimablePrincipalUserIds(operator.getUserId(), LocalDateTime.now());
        boolean matched = false;
        for (String principalUserId : allowedPrincipals) {
            if (candidates.contains(principalUserId)) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_CLAIM_PERMISSION_DENIED,
                    "current user is not a task candidate or delegate");
        }
    }

    private List<String> claimablePrincipalUserIds(String operatorUserId, LocalDateTime at) {
        List<String> principals = new ArrayList<String>();
        principals.add(operatorUserId);
        if (delegateProvider == null) {
            return principals;
        }
        List<DelegateRelationDTO> relations = delegateProvider.findPrincipals(operatorUserId, at);
        if (relations == null || relations.isEmpty()) {
            return principals;
        }
        for (DelegateRelationDTO relation : relations) {
            if (relation == null || !hasText(relation.getPrincipalUserId())) {
                continue;
            }
            if (hasText(relation.getDelegateUserId()) && !operatorUserId.equals(relation.getDelegateUserId())) {
                continue;
            }
            if (!principals.contains(relation.getPrincipalUserId())) {
                principals.add(relation.getPrincipalUserId());
            }
        }
        return principals;
    }

    private void validateUnclaim(ProcessActiveTaskEntity task, UserContext operator) {
        if (!TaskStatusEnum.CLAIMED.name().equals(task.getTaskStatus())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_NOT_CLAIMED,
                    "task is not claimed");
        }
        if (!operator.getUserId().equals(task.getAssigneeUserId())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.TASK_PERMISSION_DENIED,
                    "only the current assignee can unclaim the task");
        }
    }

    private TaskActionResult result(ProcessInstanceEntity instance,
                                    TaskOperationRequest request,
                                    ActionTypeEnum action,
                                    UserContext operator,
                                    ProcessActiveTaskEntity updated,
                                    WorkflowEventTypeEnum eventType) {
        TaskActionResult result = new TaskActionResult();
        result.setOperationId(request.getOperationId());
        ProcessInstanceDTO instanceDTO = RuntimeModelMapper.toDto(instance);
        instanceDTO.setCreatedTasks(Collections.<TaskDTO>emptyList());
        result.setInstance(instanceDTO);
        result.setArchivedTasks(Collections.<HistoryTaskDTO>emptyList());
        result.setCreatedTasks(Collections.<TaskDTO>emptyList());
        result.setUpdatedTasks(Collections.singletonList(RuntimeModelMapper.toDto(updated, null, null)));
        writeAudit(instance, request, action, operator, updated);
        publish(request.getOperationId(), eventType, action, result, operator);
        return result;
    }

    private void writeAudit(ProcessInstanceEntity instance,
                            TaskOperationRequest request,
                            ActionTypeEnum action,
                            UserContext operator,
                            ProcessActiveTaskEntity updated) {
        AuditLogCommand command = new AuditLogCommand();
        command.setInstanceId(instance.getId());
        command.setOperationId(request.getOperationId());
        command.setTargetType(OperationTargetTypeEnum.TASK);
        command.setTargetId(request.getTaskId());
        command.setActionType(action.name());
        command.setOperatorId(operator.getUserId());
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("schemaVersion", Integer.valueOf(1));
        detail.put("controlAction", Boolean.TRUE);
        detail.put("taskStatus", updated.getTaskStatus());
        detail.put("taskVersion", updated.getLockVersion());
        command.setDetail(detail);
        auditLogWriter.append(command);
    }

    private void publish(String operationId,
                         WorkflowEventTypeEnum eventType,
                         ActionTypeEnum action,
                         TaskActionResult result,
                         UserContext operator) {
        com.flowmind.platform.api.dto.WorkflowEvent event = new com.flowmind.platform.api.dto.WorkflowEvent();
        event.setEventId(operationId + ":" + eventType.name() + ":" + result.getUpdatedTasks().get(0).getTaskId());
        event.setOperationId(operationId);
        event.setEventType(eventType);
        event.setProcessCode(result.getInstance().getProcessCode());
        event.setInstanceId(result.getInstance().getInstanceId());
        event.setActionType(action);
        event.setOperator(operator);
        event.setArchivedTasks(new ArrayList<HistoryTaskDTO>(result.getArchivedTasks()));
        event.setCreatedTasks(Collections.<TaskDTO>emptyList());
        event.setVariables(result.getInstance().getVariables());
        event.setOccurredAt(LocalDateTime.now());
        callbackService.publishCallback(event);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
