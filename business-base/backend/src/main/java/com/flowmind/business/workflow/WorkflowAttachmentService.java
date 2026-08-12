package com.flowmind.business.workflow;

import com.flowmind.business.common.OperationIdFactory;
import com.flowmind.business.platform.PlatformDtoMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.business.workflow.dto.WorkflowDetailResponse;
import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import com.flowmind.platform.api.request.AttachmentUploadItem;
import com.flowmind.platform.api.request.ReplaceInstanceAttachmentRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Business multipart 与 Platform 原子附件替换契约之间的适配层。
 */
@Service
public class WorkflowAttachmentService {
    private static final String DOMAIN = "workflow-attachment-replace";

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
        item.setFileName(safeFileName(file.getOriginalFilename()));
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

    private String safeFileName(String value) {
        String name = value == null ? "attachment" : value.replace('\r', '_').replace('\n', '_')
                .replace('\\', '_').replace('/', '_').trim();
        return name.isEmpty() ? "attachment" : name;
    }
}
