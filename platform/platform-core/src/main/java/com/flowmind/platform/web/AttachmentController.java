package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.AttachmentDownloadDTO;
import com.flowmind.platform.api.dto.AttachmentQuery;
import com.flowmind.platform.api.request.DeleteAttachmentRequest;
import com.flowmind.platform.api.request.DownloadAttachmentRequest;
import com.flowmind.platform.api.request.SaveInstanceAttachmentRequest;
import com.flowmind.platform.api.request.SaveTaskAttachmentRequest;
import com.flowmind.platform.api.service.AttachmentService;
import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 附件 REST 适配层，仅转换路径参数和服务请求。 */
@RestController
@RequestMapping("/api/platform")
public class AttachmentController {
    private final AttachmentService attachmentService;
    public AttachmentController(AttachmentService attachmentService) { this.attachmentService = attachmentService; }

    @PostMapping("/instances/{instanceId}/attachments")
    public AttachmentDTO saveInstance(@PathVariable String instanceId, @RequestBody SaveInstanceAttachmentRequest request) {
        rejectConflict(instanceId, request.getInstanceId(), "instanceId");
        request.setInstanceId(instanceId); return attachmentService.saveInstanceAttachment(request);
    }
    @PostMapping("/tasks/{taskId}/attachments")
    public AttachmentDTO saveTask(@PathVariable String taskId, @RequestBody SaveTaskAttachmentRequest request) {
        rejectConflict(taskId, request.getTaskId(), "taskId");
        request.setTaskId(taskId); return attachmentService.saveTaskAttachment(request);
    }
    @GetMapping("/attachments")
    public List<AttachmentDTO> query(AttachmentQuery query) { return attachmentService.queryAttachments(query); }
    @GetMapping("/attachments/{attachmentId}/download")
    public AttachmentDownloadDTO download(@PathVariable String attachmentId, DownloadAttachmentRequest request) {
        rejectConflict(attachmentId, request.getAttachmentId(), "attachmentId");
        request.setAttachmentId(attachmentId); return attachmentService.downloadAttachment(request);
    }
    @DeleteMapping("/attachments/{attachmentId}")
    public void delete(@PathVariable String attachmentId, @RequestBody DeleteAttachmentRequest request) {
        rejectConflict(attachmentId, request.getAttachmentId(), "attachmentId");
        request.setAttachmentId(attachmentId); attachmentService.deleteAttachment(request);
    }

    private void rejectConflict(String pathValue, String bodyValue, String field) {
        if (bodyValue != null && !bodyValue.trim().isEmpty() && !pathValue.equals(bodyValue)) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "path " + field + " does not match request body");
        }
    }
}
