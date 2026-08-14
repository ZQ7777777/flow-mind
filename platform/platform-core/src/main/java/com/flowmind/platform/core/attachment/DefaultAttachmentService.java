package com.flowmind.platform.core.attachment;

import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentDownloadDTO;
import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.api.dto.AttachmentTemplateCheckResult;
import com.flowmind.platform.api.dto.FileContent;
import com.flowmind.platform.api.dto.StoredFile;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.AttachmentAccessActionEnum;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.AttachmentAccessRequest;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.CheckAttachmentRequest;
import com.flowmind.platform.api.request.DeleteAttachmentRequest;
import com.flowmind.platform.api.request.DownloadAttachmentRequest;
import com.flowmind.platform.api.request.OperationRequest;
import com.flowmind.platform.api.request.ReplaceInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import com.flowmind.platform.api.request.StoreFileRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.api.spi.CurrentUserProvider;
import com.flowmind.platform.api.spi.FileStorageProvider;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
import com.flowmind.platform.core.runtime.RuntimeOperationExecutor;
import com.flowmind.platform.core.runtime.RuntimeOperationTypes;
import com.flowmind.platform.core.runtime.RuntimeStateException;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.core.definition.OperationIdempotencyDecision;
import com.flowmind.platform.core.definition.OperationIdempotencyDecisionType;
import com.flowmind.platform.core.security.AttachmentAccessGuard;
import com.flowmind.platform.persistence.entity.ProcessActiveTaskEntity;
import com.flowmind.platform.persistence.entity.ProcessAttachmentEntity;
import com.flowmind.platform.persistence.entity.ProcessAttachmentTemplateEntity;
import com.flowmind.platform.persistence.entity.ProcessDefinitionAttachmentConfigEntity;
import com.flowmind.platform.persistence.entity.ProcessInstanceEntity;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import com.flowmind.platform.persistence.repository.ActiveTaskRepository;
import com.flowmind.platform.persistence.repository.ProcessAttachmentRepository;
import com.flowmind.platform.persistence.repository.ProcessAttachmentTemplateRepository;
import com.flowmind.platform.persistence.repository.ProcessDefinitionAttachmentConfigRepository;
import com.flowmind.platform.persistence.repository.ProcessInstanceRepository;
import com.flowmind.platform.persistence.repository.ProcessNodeRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** M4 运行期附件服务，统一收口授权、模板校验和文件存储。 */
@Service
public class DefaultAttachmentService implements AttachmentService {
    private final ProcessAttachmentRepository attachmentRepository;
    private final ProcessInstanceRepository instanceRepository;
    private final ActiveTaskRepository activeTaskRepository;
    private final ProcessDefinitionAttachmentConfigRepository configRepository;
    private final ProcessAttachmentTemplateRepository templateRepository;
    private final FileStorageProvider storageProvider;
    private final AttachmentAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final RuntimeOperationExecutor operationExecutor;
    private final ProcessNodeRepository nodeRepository;

    public DefaultAttachmentService(ProcessAttachmentRepository attachmentRepository, ProcessInstanceRepository instanceRepository,
                                    ActiveTaskRepository activeTaskRepository, ProcessDefinitionAttachmentConfigRepository configRepository,
                                    ProcessAttachmentTemplateRepository templateRepository, FileStorageProvider storageProvider,
                                    AttachmentAccessGuard accessGuard, CurrentUserProvider currentUserProvider) {
        this(attachmentRepository, instanceRepository, activeTaskRepository, configRepository, templateRepository,
                storageProvider, accessGuard, currentUserProvider, null, null);
    }

    public DefaultAttachmentService(ProcessAttachmentRepository attachmentRepository, ProcessInstanceRepository instanceRepository,
                                    ActiveTaskRepository activeTaskRepository, ProcessDefinitionAttachmentConfigRepository configRepository,
                                    ProcessAttachmentTemplateRepository templateRepository, FileStorageProvider storageProvider,
                                    AttachmentAccessGuard accessGuard, CurrentUserProvider currentUserProvider,
                                    RuntimeOperationExecutor operationExecutor) {
        this(attachmentRepository, instanceRepository, activeTaskRepository, configRepository, templateRepository,
                storageProvider, accessGuard, currentUserProvider, operationExecutor, null);
    }

    @Autowired
    public DefaultAttachmentService(ProcessAttachmentRepository attachmentRepository, ProcessInstanceRepository instanceRepository,
                                    ActiveTaskRepository activeTaskRepository, ProcessDefinitionAttachmentConfigRepository configRepository,
                                    ProcessAttachmentTemplateRepository templateRepository, FileStorageProvider storageProvider,
                                    AttachmentAccessGuard accessGuard, CurrentUserProvider currentUserProvider,
                                    RuntimeOperationExecutor operationExecutor, ProcessNodeRepository nodeRepository) {
        this.attachmentRepository = attachmentRepository; this.instanceRepository = instanceRepository;
        this.activeTaskRepository = activeTaskRepository; this.configRepository = configRepository;
        this.templateRepository = templateRepository; this.storageProvider = storageProvider;
        this.accessGuard = accessGuard; this.currentUserProvider = currentUserProvider; this.operationExecutor = operationExecutor;
        this.nodeRepository = nodeRepository;
    }

    @Override @Transactional
    public AttachmentDTO saveInstanceAttachment(SaveInstanceAttachmentRequest request) {
        requireText(request == null ? null : request.getOperationId(), "operationId is required");
        requireText(request == null ? null : request.getInstanceId(), "instanceId is required");
        requireText(request == null ? null : request.getSourceTaskId(), "sourceTaskId is required");
        if (request.getExpectedTaskVersion() == null) throw invalid("expectedTaskVersion is required");
        return save(request, request.getInstanceId(), request.getSourceTaskId(), request.getExpectedTaskVersion(), request.getOperatorUserId(),
                request.getOperationId(), request.getAttachment(), AttachmentOwnerTypeEnum.INSTANCE);
    }

    @Override @Transactional
    public AttachmentDTO saveTaskAttachment(SaveTaskAttachmentRequest request) {
        requireText(request == null ? null : request.getOperationId(), "operationId is required");
        requireText(request == null ? null : request.getInstanceId(), "instanceId is required");
        requireText(request == null ? null : request.getTaskId(), "taskId is required");
        if (request.getExpectedTaskVersion() == null) throw invalid("expectedTaskVersion is required");
        return save(request, request.getInstanceId(), request.getTaskId(), request.getExpectedTaskVersion(), request.getOperatorUserId(),
                request.getOperationId(), request.getAttachment(), AttachmentOwnerTypeEnum.TASK);
    }

    @Override
    @Transactional
    public AttachmentDTO replaceInstanceAttachment(ReplaceInstanceAttachmentRequest request) {
        requireText(request == null ? null : request.getOperationId(), "operationId is required");
        requireText(request == null ? null : request.getInstanceId(), "instanceId is required");
        requireText(request == null ? null : request.getAttachmentId(), "attachmentId is required");
        requireText(request == null ? null : request.getSourceTaskId(), "sourceTaskId is required");
        if (request.getExpectedTaskVersion() == null) throw invalid("expectedTaskVersion is required");
        UserContext user = requireUser(request.getOperatorUserId());
        if (operationExecutor != null) {
            OperationIdempotencyDecision decision = operationExecutor.begin(request,
                    RuntimeOperationTypes.ATTACHMENT_REPLACE, user.getUserId(), request.getInstanceId(),
                    request.getSourceTaskId(), LocalDateTime.now());
            if (OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType())) {
                return operationExecutor.replayResult(decision, AttachmentDTO.class);
            }
            operationExecutor.assertExecutable(decision);
            try {
                AttachmentDTO result = replaceInternal(request, user);
                operationExecutor.markSuccess(request.getOperationId(), result);
                return result;
            } catch (RuntimeValidationException ex) {
                markDeterministicFailureBestEffort(request.getOperationId(), ex.getErrorCode());
                throw ex;
            } catch (RuntimeStateException ex) {
                markDeterministicFailureBestEffort(request.getOperationId(), ex.getErrorCode());
                throw ex;
            }
        }
        return replaceInternal(request, user);
    }

    private AttachmentDTO replaceInternal(ReplaceInstanceAttachmentRequest request, UserContext user) {
        AttachmentUploadItem item = request.getAttachment();
        if (item == null || item.getOwnerType() != AttachmentOwnerTypeEnum.INSTANCE) {
            throw invalid("attachment ownerType does not match replace endpoint");
        }
        ProcessInstanceEntity instance = requireInstance(request.getInstanceId());
        ProcessActiveTaskEntity task = requireOpenTask(request.getSourceTaskId(), request.getInstanceId(),
                request.getExpectedTaskVersion());
        requireStarterReplacementTask(task);
        ProcessAttachmentEntity old = requireActiveAttachment(request.getAttachmentId());
        if (!request.getInstanceId().equals(old.getInstanceId())
                || !AttachmentOwnerTypeEnum.INSTANCE.name().equals(old.getOwnerType())
                || !old.getAttachmentCode().equals(item.getAttachmentCode())) {
            throw invalid("replacement must keep the same instance and attachment code");
        }
        requireOwner(user, old);
        allow(user, AttachmentAccessActionEnum.DELETE, request.getInstanceId(), task.getId(), old.getId(),
                AttachmentOwnerTypeEnum.INSTANCE);
        allow(user, AttachmentAccessActionEnum.UPLOAD, request.getInstanceId(), task.getId(), null,
                AttachmentOwnerTypeEnum.INSTANCE);
        ProcessDefinitionAttachmentConfigEntity config = requireConfig(instance, task.getNodeCode(),
                item.getAttachmentCode());
        validateItem(item, config);
        int maxCount = config.getMaxCount() == null ? Integer.MAX_VALUE : config.getMaxCount().intValue();
        long activeWithoutOld = attachmentRepository.countActiveByInstanceAndCode(request.getInstanceId(),
                item.getAttachmentCode()) - 1L;
        if (activeWithoutOld >= maxCount) {
            throw new RuntimeValidationException(RuntimeErrorCodes.ATTACHMENT_COUNT_EXCEEDED,
                    "attachment count exceeds configured maximum");
        }
        StoredFile stored;
        try {
            stored = storageProvider.store(new StoreFileRequest(request.getOperationId(), item.getFileName(),
                    item.getContentType(), item.getSizeBytes(), item.getContent()));
        } catch (RuntimeException ex) {
            throw new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_STORAGE_FAILED, "file storage failed");
        }
        registerRollbackCleanup(stored.getStorageKey());
        LocalDateTime changedAt = LocalDateTime.now();
        if (attachmentRepository.softDeleteForReplacement(old.getId(), request.getSourceTaskId(),
                request.getInstanceId(), request.getExpectedTaskVersion(), user.getUserId(), changedAt) != 1) {
            throw new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_SOURCE_TASK_INVALID,
                    "replacement task or attachment is no longer active");
        }
        ProcessAttachmentEntity entity = new ProcessAttachmentEntity();
        entity.setId(UUID.randomUUID().toString()); entity.setInstanceId(request.getInstanceId());
        entity.setTaskId(request.getSourceTaskId()); entity.setOwnerType(AttachmentOwnerTypeEnum.INSTANCE.name());
        entity.setAttachmentCode(item.getAttachmentCode()); entity.setFieldCode(item.getFieldCode());
        entity.setFileName(stored.getFileName()); entity.setContentType(stored.getContentType());
        entity.setSizeBytes(stored.getSizeBytes()); entity.setStorageKey(stored.getStorageKey());
        entity.setUploadedBy(user.getUserId()); entity.setUploadedAt(changedAt);
        if (attachmentRepository.insertWhenTaskOpenAndWithinLimit(entity, request.getExpectedTaskVersion(),
                Integer.valueOf(maxCount)) != 1) {
            throw new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_SOURCE_TASK_INVALID,
                    "replacement task is no longer active");
        }
        registerCommitCleanup(old.getStorageKey());
        return dto(entity);
    }

    private void requireStarterReplacementTask(ProcessActiveTaskEntity task) {
        if (task.getTaskGroupId() != null || task.getBranchKey() != null || nodeRepository == null) {
            throw invalid("only a serial STARTER task may replace instance attachments");
        }
        ProcessNodeEntity node = nodeRepository.findByDefinitionIdAndNodeCode(task.getDefinitionId(),
                task.getNodeCode());
        if (node == null || !"USER_TASK".equals(node.getNodeType())
                || !"STARTER".equals(node.getApproverRuleType())) {
            throw invalid("only a STARTER task may replace instance attachments");
        }
    }

    private AttachmentDTO save(OperationRequest operationRequest, String instanceId, String taskId, Long version, String operatorId, String operationId,
                                AttachmentUploadItem item, AttachmentOwnerTypeEnum ownerType) {
        UserContext user = requireUser(operatorId);
        if (operationExecutor != null) {
            OperationIdempotencyDecision decision = operationExecutor.begin(operationRequest,
                    RuntimeOperationTypes.ATTACHMENT_UPLOAD, user.getUserId(), instanceId, taskId, LocalDateTime.now());
            if (OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType())) {
                return operationExecutor.replayResult(decision, AttachmentDTO.class);
            }
            operationExecutor.assertExecutable(decision);
            try {
                AttachmentDTO result = saveInternal(instanceId, taskId, version, user, operationId, item, ownerType);
                operationExecutor.markSuccess(operationId, result);
                return result;
            } catch (RuntimeValidationException ex) {
                markDeterministicFailureBestEffort(operationId, ex.getErrorCode());
                throw ex;
            } catch (RuntimeStateException ex) {
                markDeterministicFailureBestEffort(operationId, ex.getErrorCode());
                throw ex;
            }
        }
        return saveInternal(instanceId, taskId, version, user, operationId, item, ownerType);
    }

    private void markDeterministicFailureBestEffort(String operationId, String errorCode) {
        try {
            operationExecutor.markDeterministicFailure(operationId, errorCode);
        } catch (DataAccessException ex) {
            // 失败标记只是幂等辅助状态，不能覆盖原始附件业务错误。
        }
    }

    private AttachmentDTO saveInternal(String instanceId, String taskId, Long version, UserContext user, String operationId,
                                       AttachmentUploadItem item, AttachmentOwnerTypeEnum ownerType) {
        if (item == null || item.getOwnerType() != ownerType) throw invalid("attachment ownerType does not match save endpoint");
        ProcessInstanceEntity instance = requireInstance(instanceId);
        ProcessActiveTaskEntity task = requireOpenTask(taskId, instanceId, version);
        allow(user, AttachmentAccessActionEnum.UPLOAD, instanceId, taskId, null, ownerType);
        ProcessDefinitionAttachmentConfigEntity config = requireConfig(instance, task.getNodeCode(), item.getAttachmentCode());
        validateItem(item, config);
        int maxCount = config.getMaxCount() == null ? Integer.MAX_VALUE : config.getMaxCount().intValue();
        if (config.getMaxCount() != null && attachmentRepository.countActiveByInstanceAndCode(instanceId, item.getAttachmentCode()) >= maxCount) {
            throw new RuntimeValidationException(RuntimeErrorCodes.ATTACHMENT_COUNT_EXCEEDED, "attachment count exceeds configured maximum");
        }
        StoredFile stored;
        try { stored = storageProvider.store(new StoreFileRequest(operationId, item.getFileName(), item.getContentType(), item.getSizeBytes(), item.getContent())); }
        catch (RuntimeException ex) { throw new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_STORAGE_FAILED, "file storage failed"); }
        registerRollbackCleanup(stored.getStorageKey());
        ProcessAttachmentEntity entity = new ProcessAttachmentEntity();
        entity.setId(UUID.randomUUID().toString()); entity.setInstanceId(instanceId); entity.setTaskId(taskId); entity.setOwnerType(ownerType.name());
        entity.setAttachmentCode(item.getAttachmentCode()); entity.setFieldCode(item.getFieldCode()); entity.setFileName(stored.getFileName());
        entity.setContentType(stored.getContentType()); entity.setSizeBytes(stored.getSizeBytes()); entity.setStorageKey(stored.getStorageKey());
        entity.setUploadedBy(user.getUserId()); entity.setUploadedAt(LocalDateTime.now());
        if (attachmentRepository.insertWhenTaskOpenAndWithinLimit(entity, version, Integer.valueOf(maxCount)) != 1) {
            if (config.getMaxCount() != null && attachmentRepository.countActiveByInstanceAndCode(instanceId, item.getAttachmentCode()) >= maxCount) {
                throw new RuntimeValidationException(RuntimeErrorCodes.ATTACHMENT_COUNT_EXCEEDED,
                        "attachment count exceeds configured maximum");
            }
            throw new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_SOURCE_TASK_INVALID,
                    "source task is no longer active");
        }
        return dto(entity);
    }

    @Override
    public AttachmentDownloadDTO downloadAttachment(DownloadAttachmentRequest request) {
        UserContext user = requireUser(request == null ? null : request.getOperatorUserId());
        ProcessAttachmentEntity attachment = requireActiveAttachment(request == null ? null : request.getAttachmentId());
        allow(user, AttachmentAccessActionEnum.DOWNLOAD, attachment.getInstanceId(), attachment.getTaskId(), attachment.getId(), owner(attachment));
        FileContent content;
        try { content = storageProvider.load(attachment.getStorageKey()); }
        catch (RuntimeException ex) { throw new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_STORAGE_FAILED, "attachment content is unavailable"); }
        AttachmentDownloadDTO result = new AttachmentDownloadDTO(); result.setAttachment(dto(attachment)); result.setContent(content.getContent()); return result;
    }

    @Override
    public List<AttachmentDTO> queryAttachments(AttachmentQuery query) {
        if (query == null) throw invalid("attachment query is required"); requireText(query.getInstanceId(), "instanceId is required");
        UserContext user = requireUser(query.getOperatorUserId());
        allow(user, AttachmentAccessActionEnum.VIEW, query.getInstanceId(), query.getTaskId(), null, query.getOwnerType());
        List<AttachmentDTO> result = new ArrayList<AttachmentDTO>();
        for (ProcessAttachmentEntity entity : attachmentRepository.queryActive(query)) result.add(dto(entity));
        return result;
    }

    @Override @Transactional
    public void deleteAttachment(DeleteAttachmentRequest request) {
        requireText(request == null ? null : request.getOperationId(), "operationId is required");
        UserContext user = requireUser(request == null ? null : request.getOperatorUserId());
        if (operationExecutor != null) {
            OperationIdempotencyDecision decision = operationExecutor.begin(request, RuntimeOperationTypes.ATTACHMENT_DELETE,
                    user.getUserId(), null, null, LocalDateTime.now());
            if (OperationIdempotencyDecisionType.REPLAY_SUCCESS.equals(decision.getType())) {
                retryDeletedStorageCleanup(request == null ? null : request.getAttachmentId(), user);
                return;
            }
            operationExecutor.assertExecutable(decision);
            try {
                deleteInternal(request, user);
                operationExecutor.markSuccess(request.getOperationId(), Boolean.TRUE);
                return;
            } catch (RuntimeValidationException ex) {
                markDeterministicFailureBestEffort(request.getOperationId(), ex.getErrorCode());
                throw ex;
            } catch (RuntimeStateException ex) {
                markDeterministicFailureBestEffort(request.getOperationId(), ex.getErrorCode());
                throw ex;
            }
        }
        deleteInternal(request, user);
    }

    private void deleteInternal(DeleteAttachmentRequest request, UserContext user) {
        ProcessAttachmentEntity attachment = requireAttachment(request == null ? null : request.getAttachmentId());
        requireOwner(user, attachment);
        allow(user, AttachmentAccessActionEnum.DELETE, attachment.getInstanceId(), attachment.getTaskId(), attachment.getId(), owner(attachment));
        // A prior soft delete is authoritative; retry only repeats best-effort storage cleanup.
        if (Boolean.TRUE.equals(attachment.getDeleted())) {
            registerCommitCleanup(attachment.getStorageKey());
            return;
        }
        if (attachmentRepository.softDelete(attachment.getId(), user.getUserId(), LocalDateTime.now()) != 1) {
            throw new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_NOT_FOUND, "attachment no longer exists");
        }
        registerCommitCleanup(attachment.getStorageKey());
    }

    private void retryDeletedStorageCleanup(String attachmentId, UserContext user) {
        if (attachmentId == null || attachmentId.trim().isEmpty()) {
            return;
        }
        ProcessAttachmentEntity attachment = attachmentRepository.findById(attachmentId);
        if (attachment != null && Boolean.TRUE.equals(attachment.getDeleted())) {
            requireOwner(user, attachment);
            registerCommitCleanup(attachment.getStorageKey());
        }
    }

    @Override
    public AttachmentTemplateCheckResult checkRequiredAttachments(CheckAttachmentRequest request) {
        if (request == null) throw invalid("attachment check request is required");
        UserContext user = requireUser(request.getOperatorUserId()); ProcessInstanceEntity instance = requireInstance(request.getInstanceId());
        allow(user, AttachmentAccessActionEnum.VIEW, instance.getId(), null, null, null);
        AttachmentTemplateCheckResult result = new AttachmentTemplateCheckResult(); List<String> missing = new ArrayList<String>(); List<String> errors = new ArrayList<String>();
        for (ProcessDefinitionAttachmentConfigEntity config : configsForNode(instance, request.getNodeCode())) {
            long count = attachmentRepository.countActiveByInstanceAndCode(instance.getId(), config.getAttachmentCode());
            if (Boolean.TRUE.equals(config.getRequired()) && count < config.getMinCount().longValue()) { missing.add(config.getAttachmentCode()); errors.add("missing required attachment: " + config.getAttachmentCode()); }
            if (config.getMaxCount() != null && count > config.getMaxCount().longValue()) errors.add("attachment count exceeds maximum: " + config.getAttachmentCode());
        }
        result.setMissingAttachmentCodes(missing); result.setErrors(errors); result.setPassed(errors.isEmpty()); return result;
    }

    private ProcessDefinitionAttachmentConfigEntity requireConfig(ProcessInstanceEntity instance, String nodeCode, String code) {
        for (ProcessDefinitionAttachmentConfigEntity value : configsForNode(instance, nodeCode)) if (code != null && code.equals(value.getAttachmentCode())) return value;
        throw new RuntimeValidationException(RuntimeErrorCodes.ATTACHMENT_TYPE_NOT_ALLOWED, "attachment is not configured for the current node");
    }
    private List<ProcessDefinitionAttachmentConfigEntity> configsForNode(ProcessInstanceEntity instance, String nodeCode) {
        if (instance.getAttachmentConfigId() == null) return Collections.emptyList(); List<ProcessDefinitionAttachmentConfigEntity> result = new ArrayList<ProcessDefinitionAttachmentConfigEntity>();
        for (ProcessDefinitionAttachmentConfigEntity config : configRepository.findByDefinitionIdAndAttachmentConfigId(instance.getDefinitionId(), instance.getAttachmentConfigId())) {
            List<String> nodes = RuntimeJsonCodec.readStringList(config.getApplicableNodeCodes()); if (nodes.isEmpty() || nodes.contains(nodeCode)) result.add(config);
        } return result;
    }
    private void validateItem(AttachmentUploadItem item, ProcessDefinitionAttachmentConfigEntity config) {
        requireText(item.getAttachmentCode(), "attachmentCode is required"); requireText(item.getFileName(), "fileName is required");
        if (item.getContent() == null || item.getContent().length == 0 || item.getSizeBytes() == null || item.getSizeBytes().longValue() != item.getContent().length) throw invalid("attachment content and sizeBytes do not match");
        ProcessAttachmentTemplateEntity template = templateRepository.findById(config.getAttachmentTemplateId()).orElseThrow(() -> invalid("attachment template does not exist"));
        if (item.getSizeBytes().longValue() > template.getMaxSizeBytes().longValue()) throw new RuntimeValidationException(RuntimeErrorCodes.ATTACHMENT_TOO_LARGE, "attachment exceeds configured maximum size");
        String extension = extension(item.getFileName()); boolean allowed = false;
        for (String allowedExtension : extensions(template.getAllowedExtensions())) if (extension.equalsIgnoreCase(allowedExtension)) allowed = true;
        if (!allowed) throw new RuntimeValidationException(RuntimeErrorCodes.ATTACHMENT_TYPE_NOT_ALLOWED, "attachment extension is not allowed");
    }
    private List<String> extensions(String json) { try { return RuntimeJsonCodec.readStringList(json); } catch (IllegalArgumentException ex) { List<String> values = new ArrayList<String>(); if (json != null) for (String value : json.split(",")) values.add(value.trim().replace(".", "")); return values; } }
    private String extension(String name) { int index = name.lastIndexOf('.'); return index < 1 || index == name.length() - 1 ? "" : name.substring(index + 1); }
    private ProcessInstanceEntity requireInstance(String id) { requireText(id, "instanceId is required"); ProcessInstanceEntity value = instanceRepository.findById(id); if (value == null) throw new RuntimeStateException(RuntimeErrorCodes.INSTANCE_NOT_FOUND, "process instance does not exist"); return value; }
    private ProcessActiveTaskEntity requireOpenTask(String id, String instanceId, Long expectedVersion) { requireText(id, "source task is required"); ProcessActiveTaskEntity value = activeTaskRepository.findById(id); if (value == null || !instanceId.equals(value.getInstanceId()) || !("ACTIVE".equals(value.getTaskStatus()) || "CLAIMED".equals(value.getTaskStatus())) || (expectedVersion != null && !expectedVersion.equals(value.getLockVersion()))) throw new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_SOURCE_TASK_INVALID, "source task is not active"); return value; }
    private ProcessAttachmentEntity requireActiveAttachment(String id) { requireText(id, "attachmentId is required"); ProcessAttachmentEntity value = attachmentRepository.findById(id); if (value == null || Boolean.TRUE.equals(value.getDeleted())) throw new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_NOT_FOUND, "attachment does not exist"); return value; }
    private ProcessAttachmentEntity requireAttachment(String id) { requireText(id, "attachmentId is required"); ProcessAttachmentEntity value = attachmentRepository.findById(id); if (value == null) throw new RuntimeStateException(RuntimeErrorCodes.ATTACHMENT_NOT_FOUND, "attachment does not exist"); return value; }
    private UserContext requireUser(String operatorId) { UserContext user = currentUserProvider == null ? null : currentUserProvider.getCurrentUser(); if (user == null || user.getUserId() == null || !user.getUserId().equals(operatorId)) throw new RuntimeValidationException(RuntimeErrorCodes.ATTACHMENT_PERMISSION_DENIED, "operator does not match current user"); return user; }
    private void requireOwner(UserContext user, ProcessAttachmentEntity attachment) { if (user == null || attachment == null || attachment.getUploadedBy() == null || !attachment.getUploadedBy().equals(user.getUserId())) throw new RuntimeValidationException(RuntimeErrorCodes.ATTACHMENT_PERMISSION_DENIED, "only the attachment uploader may modify it"); }
    private void allow(UserContext user, AttachmentAccessActionEnum action, String instanceId, String taskId, String attachmentId, AttachmentOwnerTypeEnum owner) { if (accessGuard == null || !accessGuard.isAllowed(new AttachmentAccessRequest(user.getUserId(), user.getUserName(), action, instanceId, taskId, attachmentId, owner))) throw new RuntimeValidationException(RuntimeErrorCodes.ATTACHMENT_PERMISSION_DENIED, "attachment access is denied"); }
    private AttachmentOwnerTypeEnum owner(ProcessAttachmentEntity entity) { return AttachmentOwnerTypeEnum.valueOf(entity.getOwnerType()); }
    private AttachmentDTO dto(ProcessAttachmentEntity value) { AttachmentDTO dto = new AttachmentDTO(); dto.setAttachmentId(value.getId()); dto.setInstanceId(value.getInstanceId()); dto.setTaskId(value.getTaskId()); dto.setOwnerType(owner(value)); dto.setAttachmentCode(value.getAttachmentCode()); dto.setFieldCode(value.getFieldCode()); dto.setFileName(value.getFileName()); dto.setContentType(value.getContentType()); dto.setSizeBytes(value.getSizeBytes()); dto.setStorageKey(value.getStorageKey()); dto.setUploadedBy(value.getUploadedBy()); dto.setUploadedAt(value.getUploadedAt()); dto.setDeleted(value.getDeleted()); return dto; }
    private void registerRollbackCleanup(final String key) { if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) cleanup(key); } }); }
    private void registerCommitCleanup(final String key) { if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { cleanup(key); } }); else cleanup(key); }
    private void cleanup(String key) { try { storageProvider.delete(key); } catch (RuntimeException ignored) { } }
    private void requireText(String value, String message) { if (value == null || value.trim().isEmpty()) throw invalid(message); }
    private RuntimeValidationException invalid(String message) { return new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, message); }
}
