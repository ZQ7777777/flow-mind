package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.dto.AlertDTO;
import com.flowmind.platform.api.dto.AlertQuery;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.dto.ReminderDTO;
import com.flowmind.platform.api.dto.ReminderQuery;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.AlertSeverityEnum;
import com.flowmind.platform.api.enums.AlertStatusEnum;
import com.flowmind.platform.api.enums.AlertTypeEnum;
import com.flowmind.platform.api.enums.OperationTargetTypeEnum;
import com.flowmind.platform.api.enums.ReminderStatusEnum;
import com.flowmind.platform.api.enums.ReminderTypeEnum;
import com.flowmind.platform.api.request.HandleAlertRequest;
import com.flowmind.platform.api.request.RemindTaskRequest;
import com.flowmind.platform.api.request.TimeoutScanRequest;
import com.flowmind.platform.api.service.ProcessMonitorService;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.core.audit.AuditLogCommand;
import com.flowmind.platform.core.audit.AuditLogWriter;
import com.flowmind.platform.core.query.PageQueryNormalizer;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
import com.flowmind.platform.core.runtime.RuntimeModelMapper;
import com.flowmind.platform.core.runtime.RuntimeOperationExecutor;
import com.flowmind.platform.core.runtime.RuntimeOperationTypes;
import com.flowmind.platform.core.runtime.RuntimeStateException;
import com.flowmind.platform.core.runtime.RuntimeTransactionExecutor;
import com.flowmind.platform.core.runtime.RuntimeTransactionWork;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.core.runtime.AdminPermissionGuard;
import com.flowmind.platform.core.runtime.RuntimeRequestValidator;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import com.flowmind.platform.persistence.entity.ProcessReminderRecordEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.AlertRecordRepository;
import com.flowmind.platform.persistence.repository.ProcessNodeRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ReminderRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Default monitor service for M5 reminders, timeout scan and alerts. */
@Service
public class DefaultProcessMonitorService implements ProcessMonitorService {

    private final ActiveTaskRepository activeTaskRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final ReminderRecordRepository reminderRepository;
    private final AlertRecordRepository alertRepository;
    private final RuntimeRequestValidator requestValidator;
    private final RuntimeOperationExecutor operationExecutor;
    private final RuntimeTransactionExecutor transactionExecutor;
    private final AuditLogWriter auditLogWriter;
    private final MessagePublisher messagePublisher;
    private final ProcessNodeRepository processNodeRepository;
    private final TimeoutPolicyReader timeoutPolicyReader;
    private final ReminderPolicyReader reminderPolicyReader;
    private final ReminderDeduplicationGuard reminderDeduplicationGuard;
    private final TimeoutActionExecutor timeoutActionExecutor;
    private final ActionExceptionAlertWriter actionExceptionAlertWriter;
    private final AdminPermissionGuard adminPermissionGuard;

    @Autowired
    public DefaultProcessMonitorService(ActiveTaskRepository activeTaskRepository,
                                        ProcessInstanceRepository instanceRepository,
                                        ReminderRecordRepository reminderRepository,
                                        AlertRecordRepository alertRepository,
                                        RuntimeRequestValidator requestValidator,
                                        RuntimeOperationExecutor operationExecutor,
                                        RuntimeTransactionExecutor transactionExecutor,
                                        AuditLogWriter auditLogWriter,
                                        MessagePublisher messagePublisher,
                                        ProcessNodeRepository processNodeRepository,
                                        TimeoutPolicyReader timeoutPolicyReader,
                                        ReminderPolicyReader reminderPolicyReader,
                                        ReminderDeduplicationGuard reminderDeduplicationGuard,
                                        TimeoutActionExecutor timeoutActionExecutor,
                                        ActionExceptionAlertWriter actionExceptionAlertWriter,
                                        AdminPermissionGuard adminPermissionGuard) {
        this.activeTaskRepository = activeTaskRepository;
        this.instanceRepository = instanceRepository;
        this.reminderRepository = reminderRepository;
        this.alertRepository = alertRepository;
        this.requestValidator = requestValidator;
        this.operationExecutor = operationExecutor;
        this.transactionExecutor = transactionExecutor;
        this.auditLogWriter = auditLogWriter;
        this.messagePublisher = messagePublisher;
        this.processNodeRepository = processNodeRepository;
        this.timeoutPolicyReader = timeoutPolicyReader;
        this.reminderPolicyReader = reminderPolicyReader;
        this.reminderDeduplicationGuard = reminderDeduplicationGuard;
        this.timeoutActionExecutor = timeoutActionExecutor;
        this.actionExceptionAlertWriter = actionExceptionAlertWriter;
        this.adminPermissionGuard = adminPermissionGuard == null ? new AdminPermissionGuard() : adminPermissionGuard;
    }

    /** Backward compatible constructor for direct unit tests. */
    public DefaultProcessMonitorService(ActiveTaskRepository activeTaskRepository,
                                        ProcessInstanceRepository instanceRepository,
                                        ReminderRecordRepository reminderRepository,
                                        AlertRecordRepository alertRepository,
                                        RuntimeRequestValidator requestValidator,
                                        RuntimeOperationExecutor operationExecutor,
                                        RuntimeTransactionExecutor transactionExecutor,
                                        AuditLogWriter auditLogWriter,
                                        MessagePublisher messagePublisher) {
        this(activeTaskRepository, instanceRepository, reminderRepository, alertRepository, requestValidator,
                operationExecutor, transactionExecutor, auditLogWriter, messagePublisher, null, null, null, null, null,
                null, null);
    }

    @Override
    public ReminderDTO remindTask(final RemindTaskRequest request) {
        final UserContext operator = requestValidator.validateTaskIdentity(request);
        com.flowmind.platform.core.definition.OperationIdempotencyDecision decision = operationExecutor.begin(request,
                RuntimeOperationTypes.REMIND, operator.getUserId(), null, request.getTaskId(), LocalDateTime.now());
        if (decision != null && com.flowmind.platform.core.definition.OperationIdempotencyDecisionType.REPLAY_SUCCESS
                .equals(decision.getType())) {
            return operationExecutor.replayResult(decision, ReminderDTO.class);
        }
        operationExecutor.assertExecutable(decision);
        try {
            final ProcessReminderRecordEntity reminder = transactionExecutor.execute(
                    new RuntimeTransactionWork<ProcessReminderRecordEntity>() {
                        @Override
                        public ProcessReminderRecordEntity execute() {
                            ProcessActiveTaskEntity task = requireOpenTask(request.getTaskId(),
                                    request.getExpectedTaskVersion());
                            ProcessInstanceEntity instance = requireRunningInstance(task.getInstanceId());
                            operationExecutor.bindTarget(request.getOperationId(), instance.getId(), task.getId());
                            List<String> targets = resolveTargets(task);
                            ProcessReminderRecordEntity entity = new ProcessReminderRecordEntity();
                            entity.setId(UUID.randomUUID().toString());
                            entity.setInstanceId(instance.getId());
                            entity.setTaskId(task.getId());
                            entity.setReminderType(ReminderTypeEnum.MANUAL.name());
                            entity.setTargetUserIds(RuntimeJsonCodec.toJson(targets));
                            entity.setMessage(message(request, instance, task));
                            entity.setReminderStatus(ReminderStatusEnum.PENDING.name());
                            entity.setCreatedBy(operator.getUserId());
                            entity.setCreatedAt(LocalDateTime.now());
                            if (reminderRepository.insert(entity) != 1) {
                                throw new RuntimeStateException(RuntimeErrorCodes.INVALID_ACTION,
                                        "failed to create reminder record");
                            }
                            writeAudit(instance.getId(), request.getOperationId(), OperationTargetTypeEnum.TASK,
                                    task.getId(), ActionTypeEnum.REMIND.name(), operator.getUserId(), entity.getId());
                            return entity;
                        }
                    });
            ReminderDTO result = publishAndUpdate(reminder);
            operationExecutor.markSuccess(request.getOperationId(), result);
            return result;
        } catch (RuntimeValidationException ex) {
            operationExecutor.markDeterministicFailure(request.getOperationId(), ex.getErrorCode());
            throw ex;
        } catch (RuntimeStateException ex) {
            operationExecutor.markDeterministicFailure(request.getOperationId(), ex.getErrorCode());
            throw ex;
        }
    }

    @Override
    public PageResult<ReminderDTO> queryReminders(ReminderQuery query) {
        ReminderQuery normalized = query == null ? new ReminderQuery() : query;
        int pageNo = PageQueryNormalizer.normalizePageNo(normalized.getPageNo());
        int pageSize = PageQueryNormalizer.normalizePageSize(normalized.getPageSize());
        List<ReminderDTO> records = new ArrayList<ReminderDTO>();
        for (ProcessReminderRecordEntity entity : reminderRepository.query(normalized)) {
            records.add(toReminderDTO(entity));
        }
        return page(records, pageNo, pageSize, reminderRepository.count(normalized));
    }

    @Override
    public List<TaskDTO> scanTimeoutTasks(TimeoutScanRequest request) {
        LocalDateTime scanAt = request == null || request.getScanAt() == null ? LocalDateTime.now() : request.getScanAt();
        int limit = request == null || request.getLimit() == null ? 50 : Math.max(1, request.getLimit().intValue());
        boolean dryRun = request == null || Boolean.TRUE.equals(request.getDryRun());
        List<TaskDTO> results = new ArrayList<TaskDTO>();
        if (!dryRun) {
            for (ProcessActiveTaskEntity task : safeList(activeTaskRepository.findDueSoonOpenTasks(scanAt, limit))) {
                ReminderPolicy reminderPolicy = reminderPolicy(task);
                if (reminderPolicy.isEnabled()) {
                    createDueSoonReminder(task, reminderPolicy);
                }
            }
        }
        for (ProcessActiveTaskEntity task : safeList(activeTaskRepository.findTimeoutOpenTasks(scanAt, limit))) {
            results.add(RuntimeModelMapper.toDto(task, null, null));
            if (dryRun) {
                continue;
            }
            TimeoutPolicy timeoutPolicy = timeoutPolicy(task);
            ReminderPolicy reminderPolicy = reminderPolicy(task);
            if (reminderPolicy.isEnabled()) {
                createTimeoutReminder(task, reminderPolicy);
            }
            try {
                applyTimeoutAction(task, timeoutPolicy, request, scanAt);
            } catch (RuntimeStateException ex) {
                writeActionException(task, timeoutPolicy.getAction(), request, ex.getErrorCode(), ex.getMessage());
            } catch (RuntimeException ex) {
                writeActionException(task, timeoutPolicy.getAction(), request,
                        RuntimeErrorCodes.INVALID_ACTION, ex.getMessage());
            }
        }
        return results;
    }

    @Override
    public PageResult<AlertDTO> queryAlerts(AlertQuery query) {
        AlertQuery normalized = query == null ? new AlertQuery() : query;
        int pageNo = PageQueryNormalizer.normalizePageNo(normalized.getPageNo());
        int pageSize = PageQueryNormalizer.normalizePageSize(normalized.getPageSize());
        List<AlertDTO> records = new ArrayList<AlertDTO>();
        for (ProcessAlertRecordEntity entity : alertRepository.query(normalized)) {
            records.add(toAlertDTO(entity));
        }
        return page(records, pageNo, pageSize, alertRepository.count(normalized));
    }

    @Override
    public AlertDTO handleAlert(final HandleAlertRequest request) {
        validateAlertRequest(request);
        final UserContext operator = requestValidator.validateInstanceOperationIdentity(request,
                "alert:" + request.getAlertId(), request.getOperatorUserId());
        adminPermissionGuard.assertAdmin(operator);
        com.flowmind.platform.core.definition.OperationIdempotencyDecision decision = operationExecutor.begin(request,
                RuntimeOperationTypes.ALERT_HANDLE, operator.getUserId(), null, request.getAlertId(),
                LocalDateTime.now());
        if (decision != null && com.flowmind.platform.core.definition.OperationIdempotencyDecisionType.REPLAY_SUCCESS
                .equals(decision.getType())) {
            return operationExecutor.replayResult(decision, AlertDTO.class);
        }
        operationExecutor.assertExecutable(decision);
        try {
            AlertDTO result = transactionExecutor.execute(new RuntimeTransactionWork<AlertDTO>() {
                @Override
                public AlertDTO execute() {
                    ProcessAlertRecordEntity alert = alertRepository.findById(request.getAlertId());
                    if (alert == null) {
                        throw new RuntimeStateException(RuntimeErrorCodes.ALERT_NOT_FOUND, "alert does not exist");
                    }
                    if (!AlertStatusEnum.OPEN.name().equals(alert.getAlertStatus())) {
                        throw new RuntimeStateException(RuntimeErrorCodes.ALERT_ALREADY_CLOSED,
                                "alert is already closed");
                    }
                    if (alertRepository.handle(alert.getId(), request.getTargetStatus().name(), operator.getUserId(),
                            LocalDateTime.now()) != 1) {
                        throw new RuntimeStateException(RuntimeErrorCodes.ALERT_ALREADY_CLOSED,
                                "alert is already closed");
                    }
                    ProcessAlertRecordEntity updated = alertRepository.findById(alert.getId());
                    writeAudit(updated.getInstanceId(), request.getOperationId(), OperationTargetTypeEnum.INSTANCE,
                            updated.getInstanceId(), ActionTypeEnum.ALERT_HANDLE.name(), operator.getUserId(),
                            updated.getId());
                    AlertDTO dto = toAlertDTO(updated);
                    operationExecutor.markSuccess(request.getOperationId(), dto);
                    return dto;
                }
            });
            return result;
        } catch (RuntimeValidationException ex) {
            operationExecutor.markDeterministicFailure(request.getOperationId(), ex.getErrorCode());
            throw ex;
        } catch (RuntimeStateException ex) {
            operationExecutor.markDeterministicFailure(request.getOperationId(), ex.getErrorCode());
            throw ex;
        }
    }

    private ProcessActiveTaskEntity requireOpenTask(String taskId, Long expectedVersion) {
        ProcessActiveTaskEntity task = activeTaskRepository.findById(taskId);
        if (task == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.TASK_NOT_FOUND, "active task does not exist");
        }
        if (!"ACTIVE".equals(task.getTaskStatus()) && !"CLAIMED".equals(task.getTaskStatus())) {
            throw new RuntimeStateException(RuntimeErrorCodes.TASK_NOT_ACTIVE, "task is not open");
        }
        if (task.getLockVersion() == null || !task.getLockVersion().equals(expectedVersion)) {
            throw new RuntimeStateException(RuntimeErrorCodes.TASK_CONCURRENT_MODIFIED,
                    "expectedTaskVersion does not match active task");
        }
        return task;
    }

    private ProcessInstanceEntity requireRunningInstance(String instanceId) {
        ProcessInstanceEntity instance = instanceRepository.findById(instanceId);
        if (instance == null) {
            throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_NOT_FOUND, "process instance does not exist");
        }
        if (!"RUNNING".equals(instance.getInstanceStatus())) {
            throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_STATUS_INVALID,
                    "process instance is not running");
        }
        return instance;
    }

    private List<String> resolveTargets(ProcessActiveTaskEntity task) {
        if (!isBlank(task.getAssigneeUserId())) {
            return Collections.singletonList(task.getAssigneeUserId());
        }
        List<String> targets = RuntimeJsonCodec.readStringList(task.getCandidateUserIds());
        if (targets.isEmpty()) {
            throw new RuntimeValidationException(RuntimeErrorCodes.REMINDER_TARGET_EMPTY,
                    "reminder target users are empty");
        }
        return targets;
    }

    private TimeoutPolicy timeoutPolicy(ProcessActiveTaskEntity task) {
        if (processNodeRepository == null || timeoutPolicyReader == null) {
            return new TimeoutPolicy();
        }
        ProcessNodeEntity node = processNodeRepository.findByDefinitionIdAndNodeCode(task.getDefinitionId(),
                task.getNodeCode());
        return timeoutPolicyReader.read(node);
    }

    private ReminderPolicy reminderPolicy(ProcessActiveTaskEntity task) {
        if (processNodeRepository == null || reminderPolicyReader == null) {
            return new ReminderPolicy();
        }
        ProcessNodeEntity node = processNodeRepository.findByDefinitionIdAndNodeCode(task.getDefinitionId(),
                task.getNodeCode());
        return reminderPolicyReader.read(node);
    }

    private void createDueSoonReminder(ProcessActiveTaskEntity task, ReminderPolicy policy) {
        createAutomaticReminder(task, policy, ReminderTypeEnum.DUE_SOON, "system_due_soon",
                "Task due soon reminder: " + task.getNodeCode());
    }

    private void createTimeoutReminder(ProcessActiveTaskEntity task, ReminderPolicy policy) {
        createAutomaticReminder(task, policy, ReminderTypeEnum.TIMEOUT, "system_timeout",
                "Task timeout reminder: " + task.getNodeCode());
    }

    private void createAutomaticReminder(ProcessActiveTaskEntity task,
                                         ReminderPolicy policy,
                                         ReminderTypeEnum reminderType,
                                         String createdBy,
                                         String defaultMessage) {
        if (reminderRepository == null || reminderDeduplicationGuard == null || messagePublisher == null) {
            return;
        }
        ProcessReminderRecordEntity latest = reminderRepository.findLatestByTaskAndType(task.getId(),
                reminderType.name());
        if (latest != null) {
            if (ReminderStatusEnum.FAILED.name().equals(latest.getReminderStatus())) {
                publishAndUpdate(latest, task);
            }
            return;
        }
        int maxCount = policy.getMaxCount() == null ? 1 : policy.getMaxCount().intValue();
        if (!reminderDeduplicationGuard.canCreate(task.getId(), reminderType, maxCount)) {
            return;
        }
        List<String> targets = resolveTargets(task);
        ProcessReminderRecordEntity entity = new ProcessReminderRecordEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setInstanceId(task.getInstanceId());
        entity.setTaskId(task.getId());
        entity.setReminderType(reminderType.name());
        entity.setTargetUserIds(RuntimeJsonCodec.toJson(targets));
        entity.setMessage(isBlank(policy.getMessageTemplate()) ? defaultMessage : policy.getMessageTemplate());
        entity.setReminderStatus(ReminderStatusEnum.PENDING.name());
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(LocalDateTime.now());
        reminderRepository.insert(entity);
        publishAndUpdate(entity, task);
    }

    private void applyTimeoutAction(ProcessActiveTaskEntity task,
                                    TimeoutPolicy timeoutPolicy,
                                    TimeoutScanRequest request,
                                    LocalDateTime scanAt) {
        if (TimeoutPolicy.ACTION_JUMP.equals(timeoutPolicy.getAction())
                || TimeoutPolicy.ACTION_TERMINATE.equals(timeoutPolicy.getAction())
                || TimeoutPolicy.ACTION_FORCE_COMPLETE.equals(timeoutPolicy.getAction())) {
            if (timeoutActionExecutor != null) {
                timeoutActionExecutor.execute(task, timeoutPolicy,
                        request == null ? null : request.getOperatorUserId(), scanAt);
            }
            return;
        }
        if (TimeoutPolicy.ACTION_REMIND.equals(timeoutPolicy.getAction())) {
            return;
        }
        createTimeoutAlert(task, timeoutPolicy, scanAt);
    }

    private void createTimeoutAlert(ProcessActiveTaskEntity task, TimeoutPolicy timeoutPolicy, LocalDateTime scanAt) {
        if (alertRepository.findOpenByTaskAndType(task.getId(), AlertTypeEnum.TASK_TIMEOUT.name()) != null) {
            return;
        }
        ProcessAlertRecordEntity alert = new ProcessAlertRecordEntity();
        alert.setId(UUID.randomUUID().toString());
        alert.setInstanceId(task.getInstanceId());
        alert.setTaskId(task.getId());
        alert.setAlertType(AlertTypeEnum.TASK_TIMEOUT.name());
        alert.setSeverity(timeoutPolicy.getSeverity().name());
        alert.setAlertStatus(AlertStatusEnum.OPEN.name());
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("schemaVersion", Integer.valueOf(1));
        detail.put("scanAt", scanAt.toString());
        detail.put("dueAt", task.getDueAt() == null ? null : task.getDueAt().toString());
        detail.put("action", timeoutPolicy.getAction());
        alert.setDetailJson(RuntimeJsonCodec.toJson(detail));
        alert.setCreatedAt(LocalDateTime.now());
        alertRepository.insert(alert);
    }

    private void writeActionException(ProcessActiveTaskEntity task,
                                      String actionType,
                                      TimeoutScanRequest request,
                                      String errorCode,
                                      String errorSummary) {
        if (actionExceptionAlertWriter == null) {
            return;
        }
        String operationId = "timeout:" + task.getId() + ":" + actionType + ":"
                + (task.getDueAt() == null ? "unknown" : task.getDueAt());
        actionExceptionAlertWriter.write(operationId, actionType, task.getInstanceId(), task.getId(), errorCode,
                errorSummary, request == null ? null : request.getOperatorUserId());
    }

    private String message(RemindTaskRequest request, ProcessInstanceEntity instance, ProcessActiveTaskEntity task) {
        if (!isBlank(request.getComment())) {
            return request.getComment();
        }
        return "Task reminder: " + instance.getInstanceTitle() + " / " + task.getNodeCode();
    }

    private ReminderDTO publishAndUpdate(ProcessReminderRecordEntity reminder) {
        return publishAndUpdate(reminder, findTask(reminder.getTaskId()));
    }

    private ReminderDTO publishAndUpdate(ProcessReminderRecordEntity reminder, ProcessActiveTaskEntity task) {
        try {
            ProcessInstanceEntity instance = findInstance(reminder.getInstanceId());
            ProcessMessage message = new ProcessMessage();
            message.setMessageId(reminder.getId());
            message.setMessageType(messageType(reminder.getReminderType()));
            message.setTitle(messageTitle(reminder.getReminderType()));
            message.setContent(reminder.getMessage());
            message.setTargetUserIds(RuntimeJsonCodec.readStringList(reminder.getTargetUserIds()));
            message.setPayload(reminderPayload(reminder, task, instance));
            message.setCreatedAt(LocalDateTime.now());
            messagePublisher.publish(message);
            reminderRepository.markSent(reminder.getId());
        } catch (RuntimeException ex) {
            reminderRepository.markFailed(reminder.getId(), ex.getMessage());
        }
        return toReminderDTO(reminderRepository.findById(reminder.getId()));
    }

    private Map<String, Object> reminderPayload(ProcessReminderRecordEntity reminder,
                                                ProcessActiveTaskEntity task,
                                                ProcessInstanceEntity instance) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("instanceId", reminder.getInstanceId());
        if (!isBlank(reminder.getTaskId())) {
            payload.put("taskId", reminder.getTaskId());
        }
        if (instance != null) {
            payload.put("processCode", instance.getProcessCode());
            payload.put("instanceTitle", instance.getInstanceTitle());
        }
        if (task != null) {
            payload.put("nodeCode", task.getNodeCode());
            payload.put("dueAt", task.getDueAt() == null ? null : task.getDueAt().toString());
        }
        return payload;
    }

    private String messageType(String reminderType) {
        if (ReminderTypeEnum.DUE_SOON.name().equals(reminderType)) {
            return "TASK_DUE_SOON";
        }
        if (ReminderTypeEnum.TIMEOUT.name().equals(reminderType)) {
            return "TASK_TIMEOUT";
        }
        return "TASK_REMIND";
    }

    private String messageTitle(String reminderType) {
        if (ReminderTypeEnum.DUE_SOON.name().equals(reminderType)) {
            return "任务即将超时";
        }
        if (ReminderTypeEnum.TIMEOUT.name().equals(reminderType)) {
            return "任务已超时";
        }
        return "任务催办";
    }

    private ProcessActiveTaskEntity findTask(String taskId) {
        if (activeTaskRepository == null || isBlank(taskId)) {
            return null;
        }
        return activeTaskRepository.findById(taskId);
    }

    private ProcessInstanceEntity findInstance(String instanceId) {
        if (instanceRepository == null || isBlank(instanceId)) {
            return null;
        }
        return instanceRepository.findById(instanceId);
    }

    private void validateAlertRequest(HandleAlertRequest request) {
        if (request == null || isBlank(request.getOperationId()) || isBlank(request.getAlertId())
                || isBlank(request.getOperatorUserId()) || request.getTargetStatus() == null) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "operationId, alertId, operatorUserId and targetStatus are required");
        }
        if (!AlertStatusEnum.HANDLED.equals(request.getTargetStatus())
                && !AlertStatusEnum.IGNORED.equals(request.getTargetStatus())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "targetStatus must be HANDLED or IGNORED");
        }
    }

    private void writeAudit(String instanceId, String operationId, OperationTargetTypeEnum targetType,
                            String targetId, String actionType, String operatorId, String relatedId) {
        AuditLogCommand command = new AuditLogCommand();
        command.setInstanceId(instanceId);
        command.setOperationId(operationId);
        command.setTargetType(targetType);
        command.setTargetId(targetId);
        command.setActionType(actionType);
        command.setOperatorId(operatorId);
        Map<String, Object> detail = new LinkedHashMap<String, Object>();
        detail.put("schemaVersion", Integer.valueOf(1));
        detail.put("relatedId", relatedId);
        command.setDetail(detail);
        auditLogWriter.append(command);
    }

    private ReminderDTO toReminderDTO(ProcessReminderRecordEntity entity) {
        ReminderDTO dto = new ReminderDTO();
        dto.setReminderId(entity.getId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setTaskId(entity.getTaskId());
        dto.setReminderType(ReminderTypeEnum.valueOf(entity.getReminderType()));
        dto.setTargetUserIds(RuntimeJsonCodec.readStringList(entity.getTargetUserIds()));
        dto.setMessage(entity.getMessage());
        dto.setReminderStatus(ReminderStatusEnum.valueOf(entity.getReminderStatus()));
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setSentAt(entity.getSentAt());
        return dto;
    }

    private AlertDTO toAlertDTO(ProcessAlertRecordEntity entity) {
        AlertDTO dto = new AlertDTO();
        dto.setAlertId(entity.getId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setTaskId(entity.getTaskId());
        dto.setAlertType(AlertTypeEnum.valueOf(entity.getAlertType()));
        dto.setSeverity(AlertSeverityEnum.valueOf(entity.getSeverity()));
        dto.setAlertStatus(AlertStatusEnum.valueOf(entity.getAlertStatus()));
        dto.setDetail(RuntimeJsonCodec.readObjectMap(entity.getDetailJson()));
        dto.setHandledBy(entity.getHandledBy());
        dto.setHandledAt(entity.getHandledAt());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private List<ProcessActiveTaskEntity> safeList(List<ProcessActiveTaskEntity> tasks) {
        return tasks == null ? Collections.<ProcessActiveTaskEntity>emptyList() : tasks;
    }

    private <T> PageResult<T> page(List<T> records, int pageNo, int pageSize, long total) {
        PageResult<T> result = new PageResult<T>();
        result.setRecords(records);
        result.setPageNo(Integer.valueOf(pageNo));
        result.setPageSize(Integer.valueOf(pageSize));
        result.setTotal(Long.valueOf(total));
        result.setTotalPages(Integer.valueOf((int) ((total + pageSize - 1) / pageSize)));
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
