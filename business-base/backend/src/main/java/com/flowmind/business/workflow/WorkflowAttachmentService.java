package com.flowmind.business.workflow;

import com.flowmind.business.common.OperationIdFactory;
import com.flowmind.business.platform.PlatformDtoMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.business.workflow.dto.WorkflowDetailResponse;
import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentDownloadDTO;
import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.DeleteAttachmentRequest;
import com.flowmind.platform.api.request.ReplaceInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 通用附件 BFF 服务，负责可信身份、幂等号和 multipart 到平台请求的转换。
 */
@Service
public class WorkflowAttachmentService {
    private static final String DOMAIN = "workflow-attachment";
    private static final String DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_OCTET_STREAM_VALUE;

    private final PlatformFacade platformFacade;
    private final PlatformDtoMapper mapper;
    private final OperationIdFactory operationIdFactory;
    private final WorkflowQueryService queryService;

    public WorkflowAttachmentService(PlatformFacade platformFacade, PlatformDtoMapper mapper,
                                     OperationIdFactory operationIdFactory, WorkflowQueryService queryService) {
        this.platformFacade = platformFacade;
        this.mapper = mapper;
        this.operationIdFactory = operationIdFactory;
        this.queryService = queryService;
    }

    public WorkflowDetailResponse.AttachmentView replace(String taskId, String attachmentId,
                                                          Long expectedTaskVersion, MultipartFile file,
                                                          String idempotencyKey) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("替换文件不能为空");
        ProcessInstanceDetailDTO instance = queryService.authorizedTaskInstance(taskId);
        AttachmentDTO old = findAttachment(instance.getInstanceId(), attachmentId);
        if (!AttachmentOwnerTypeEnum.INSTANCE.equals(old.getOwnerType())) {
            throw new IllegalArgumentException("仅允许替换实例附件");
        }
        String userId = platformFacade.currentUser().getUserId();
        AttachmentUploadItem item = new AttachmentUploadItem();
        item.setAttachmentCode(old.getAttachmentCode());
        item.setFieldCode(old.getFieldCode());
        item.setOwnerType(AttachmentOwnerTypeEnum.INSTANCE);
        item.setFileName(sanitizeFileName(file.getOriginalFilename()));
        item.setContentType(file.getContentType());
        item.setSizeBytes(file.getSize());
        try { item.setContent(file.getBytes()); }
        catch (IOException exception) { throw new IllegalArgumentException("无法读取替换文件"); }

        ReplaceInstanceAttachmentRequest request = new ReplaceInstanceAttachmentRequest();
        request.setInstanceId(instance.getInstanceId());
        request.setAttachmentId(attachmentId);
        request.setSourceTaskId(taskId);
        request.setExpectedTaskVersion(expectedTaskVersion);
        request.setOperatorUserId(userId);
        request.setOperationId(operationIdFactory.create(DOMAIN, "replace", attachmentId, userId, idempotencyKey));
        request.setAttachment(item);
        return mapper.attachment(platformFacade.replaceInstanceAttachment(request));
    }

    private AttachmentDTO findAttachment(String instanceId, String attachmentId) {
        for (AttachmentDTO attachment : platformFacade.attachments(instanceId)) {
            if (attachment != null && attachmentId.equals(attachment.getAttachmentId())
                    && !Boolean.TRUE.equals(attachment.getDeleted())) return attachment;
        }
        throw new IllegalArgumentException("附件不存在或不可替换");
    }

    public List<WorkflowDetailResponse.AttachmentView> query(String instanceId,
                                                             String taskId,
                                                             String attachmentCode,
                                                             String fieldCode) {
        AttachmentQuery query = new AttachmentQuery();
        query.setInstanceId(trimToNull(instanceId));
        query.setTaskId(trimToNull(taskId));
        query.setAttachmentCode(trimToNull(attachmentCode));
        query.setFieldCode(trimToNull(fieldCode));
        return mapper.attachments(platformFacade.queryAttachments(query));
    }

    public WorkflowDetailResponse.AttachmentView uploadInstance(String instanceId,
                                                                MultipartFile file,
                                                                String attachmentCode,
                                                                String fieldCode,
                                                                String sourceTaskId,
                                                                Long expectedTaskVersion,
                                                                String idempotencyKey) {
        UserContext user = platformFacade.currentUser();
        SaveInstanceAttachmentRequest request = new SaveInstanceAttachmentRequest();
        request.setInstanceId(require(instanceId, "instanceId"));
        request.setSourceTaskId(require(sourceTaskId, "sourceTaskId"));
        request.setExpectedTaskVersion(require(expectedTaskVersion, "expectedTaskVersion"));
        request.setAttachment(uploadItem(file, attachmentCode, fieldCode, AttachmentOwnerTypeEnum.INSTANCE));
        request.setOperationId(operationIdFactory.create(DOMAIN, "upload-instance",
                instanceId, user.getUserId(), idempotencyKey));
        AttachmentDTO saved = platformFacade.saveInstanceAttachment(request);
        return mapper.attachments(java.util.Collections.singletonList(saved)).get(0);
    }

    public WorkflowDetailResponse.AttachmentView uploadTask(String taskId,
                                                            MultipartFile file,
                                                            String instanceId,
                                                            String attachmentCode,
                                                            String fieldCode,
                                                            Long expectedTaskVersion,
                                                            String idempotencyKey) {
        UserContext user = platformFacade.currentUser();
        SaveTaskAttachmentRequest request = new SaveTaskAttachmentRequest();
        request.setTaskId(require(taskId, "taskId"));
        request.setInstanceId(require(instanceId, "instanceId"));
        request.setExpectedTaskVersion(require(expectedTaskVersion, "expectedTaskVersion"));
        request.setAttachment(uploadItem(file, attachmentCode, fieldCode, AttachmentOwnerTypeEnum.TASK));
        request.setOperationId(operationIdFactory.create(DOMAIN, "upload-task",
                taskId, user.getUserId(), idempotencyKey));
        AttachmentDTO saved = platformFacade.saveTaskAttachment(request);
        return mapper.attachments(java.util.Collections.singletonList(saved)).get(0);
    }

    public AttachmentContent download(String attachmentId) {
        AttachmentDownloadDTO download = platformFacade.downloadAttachment(require(attachmentId, "attachmentId"));
        AttachmentDTO attachment = download.getAttachment();
        String fileName = attachment == null ? "attachment" : sanitizeFileName(attachment.getFileName());
        String contentType = attachment == null ? null : trimToNull(attachment.getContentType());
        return new AttachmentContent(fileName, contentType == null ? DEFAULT_CONTENT_TYPE : contentType,
                download.getContent() == null ? new byte[0] : download.getContent());
    }

    public void delete(String attachmentId, String idempotencyKey) {
        UserContext user = platformFacade.currentUser();
        DeleteAttachmentRequest request = new DeleteAttachmentRequest();
        request.setAttachmentId(require(attachmentId, "attachmentId"));
        request.setOperationId(operationIdFactory.create(DOMAIN, "delete",
                attachmentId, user.getUserId(), idempotencyKey));
        platformFacade.deleteAttachment(request);
    }

    private AttachmentUploadItem uploadItem(MultipartFile file,
                                            String attachmentCode,
                                            String fieldCode,
                                            AttachmentOwnerTypeEnum ownerType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        AttachmentUploadItem item = new AttachmentUploadItem();
        item.setAttachmentCode(require(attachmentCode, "attachmentCode"));
        item.setFieldCode(trimToNull(fieldCode));
        item.setOwnerType(ownerType);
        item.setFileName(sanitizeFileName(file.getOriginalFilename()));
        item.setContentType(trimToNull(file.getContentType()) == null ? DEFAULT_CONTENT_TYPE : file.getContentType());
        item.setSizeBytes(Long.valueOf(file.getSize()));
        try {
            item.setContent(file.getBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("file cannot be read", exception);
        }
        return item;
    }

    private String sanitizeFileName(String value) {
        String fileName = trimToNull(value);
        if (fileName == null) return "attachment";
        fileName = fileName.replace('\\', '/');
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) fileName = fileName.substring(slash + 1);
        fileName = fileName.replace('\r', '_').replace('\n', '_').trim();
        return fileName.isEmpty() ? "attachment" : fileName;
    }

    private String require(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) throw new IllegalArgumentException(fieldName + " is required");
        return trimmed;
    }

    private Long require(Long value, String fieldName) {
        if (value == null) throw new IllegalArgumentException(fieldName + " is required");
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class AttachmentContent {
        private final String fileName;
        private final String contentType;
        private final byte[] content;

        AttachmentContent(String fileName, String contentType, byte[] content) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.content = content;
        }

        public String getFileName() { return fileName; }
        public String getContentType() { return contentType; }
        public byte[] getContent() { return content; }
    }
}
