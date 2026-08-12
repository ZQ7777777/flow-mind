package com.flowmind.business.workflow;

import com.flowmind.business.workflow.dto.WorkflowDetailResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 申请返工附件替换 REST 适配器。 */
@RestController
@RequestMapping("/api/workflow/tasks/{taskId}/instance-attachments")
public class WorkflowAttachmentController {
    private final WorkflowAttachmentService attachmentService;

    public WorkflowAttachmentController(WorkflowAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PutMapping("/{attachmentId}")
    public WorkflowDetailResponse.AttachmentView replace(
            @PathVariable String taskId,
            @PathVariable String attachmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam Long expectedTaskVersion,
            @RequestPart("file") MultipartFile file) {
        return attachmentService.replace(taskId, attachmentId, expectedTaskVersion, file, idempotencyKey);
    }
}
