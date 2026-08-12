package com.flowmind.business.workflow;

import com.flowmind.business.workflow.dto.WorkflowDetailResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 通用附件接口，为业务前端封装平台附件能力。
 */
@RestController
@RequestMapping("/api/workflow")
public class WorkflowAttachmentController {
    private final WorkflowAttachmentService attachmentService;

    public WorkflowAttachmentController(WorkflowAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/attachments")
    public List<WorkflowDetailResponse.AttachmentView> query(
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String attachmentCode,
            @RequestParam(required = false) String fieldCode) {
        return attachmentService.query(instanceId, taskId, attachmentCode, fieldCode);
    }

    @PostMapping(value = "/instances/{instanceId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkflowDetailResponse.AttachmentView uploadInstance(
            @PathVariable String instanceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("file") MultipartFile file,
            @RequestParam String attachmentCode,
            @RequestParam(required = false) String fieldCode,
            @RequestParam String sourceTaskId,
            @RequestParam Long expectedTaskVersion) {
        return attachmentService.uploadInstance(instanceId, file, attachmentCode, fieldCode,
                sourceTaskId, expectedTaskVersion, idempotencyKey);
    }

    @PostMapping(value = "/tasks/{taskId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkflowDetailResponse.AttachmentView uploadTask(
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("file") MultipartFile file,
            @RequestParam String instanceId,
            @RequestParam String attachmentCode,
            @RequestParam(required = false) String fieldCode,
            @RequestParam Long expectedTaskVersion) {
        return attachmentService.uploadTask(taskId, file, instanceId, attachmentCode, fieldCode,
                expectedTaskVersion, idempotencyKey);
    }

    @GetMapping("/attachments/{attachmentId}/content")
    public ResponseEntity<byte[]> download(@PathVariable String attachmentId) {
        WorkflowAttachmentService.AttachmentContent content = attachmentService.download(attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(content.getContent());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable String attachmentId,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey) {
        attachmentService.delete(attachmentId, idempotencyKey);
        return ResponseEntity.noContent().build();
    }
}
