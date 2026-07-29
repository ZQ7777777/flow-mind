package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.enums.AttachmentTemplateStatusEnum;
import com.flowmind.platform.core.definition.ProcessAttachmentTemplateManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 附件模板版本 REST 适配层。
 *
 * @author FlowMind
 * @since 2026-07-28
 */
@RestController
@RequestMapping("/api/platform/attachment-templates")
@Tag(name = "Attachment Template", description = "Attachment template version APIs")
public class ProcessAttachmentTemplateController {

    private final ProcessAttachmentTemplateManager attachmentTemplateManager;

    public ProcessAttachmentTemplateController(ProcessAttachmentTemplateManager attachmentTemplateManager) {
        this.attachmentTemplateManager = attachmentTemplateManager;
    }

    @Operation(summary = "Create attachment template version")
    @PostMapping
    public ProcessAttachmentTemplateDTO createTemplate(
            @RequestBody ProcessAttachmentTemplateDTO request,
            @RequestHeader(value = "X-Flow-User-Id", required = false) String operatorUserId) {
        return attachmentTemplateManager.createTemplateVersion(request, resolveOperatorUserId(operatorUserId, request));
    }

    @Operation(summary = "Update attachment template version")
    @PutMapping("/{attachmentTemplateId}")
    public ProcessAttachmentTemplateDTO updateTemplate(
            @Parameter(description = "Attachment template id") @PathVariable String attachmentTemplateId,
            @RequestBody ProcessAttachmentTemplateDTO request,
            @RequestHeader(value = "X-Flow-User-Id", required = false) String operatorUserId) {
        request.setAttachmentTemplateId(attachmentTemplateId);
        if (request.getTemplateStatus() == null) {
            request.setTemplateStatus(AttachmentTemplateStatusEnum.ENABLED);
        }
        return attachmentTemplateManager.updateTemplate(request, resolveOperatorUserId(operatorUserId, request));
    }

    @Operation(summary = "Get attachment template version")
    @GetMapping("/{attachmentTemplateId}")
    public ProcessAttachmentTemplateDTO getTemplate(
            @Parameter(description = "Attachment template id") @PathVariable String attachmentTemplateId) {
        return attachmentTemplateManager.findById(attachmentTemplateId)
                .orElseThrow(() -> new IllegalArgumentException("attachment template not found: " + attachmentTemplateId));
    }

    @Operation(summary = "Find attachment template versions")
    @GetMapping
    public List<ProcessAttachmentTemplateDTO> findTemplates(
            @RequestParam(required = false) String attachmentCode,
            @RequestParam(required = false) String templateStatus,
            @RequestParam(required = false) Integer templateVersion) {
        return attachmentTemplateManager.findTemplates(attachmentCode, parseStatus(templateStatus), templateVersion);
    }

    private AttachmentTemplateStatusEnum parseStatus(String templateStatus) {
        if (!hasText(templateStatus)) {
            return null;
        }
        return AttachmentTemplateStatusEnum.valueOf(templateStatus.trim());
    }

    private String resolveOperatorUserId(String operatorUserId, ProcessAttachmentTemplateDTO request) {
        if (hasText(operatorUserId)) {
            return operatorUserId.trim();
        }
        if (request != null && hasText(request.getUpdatedBy())) {
            return request.getUpdatedBy().trim();
        }
        if (request != null && hasText(request.getCreatedBy())) {
            return request.getCreatedBy().trim();
        }
        return "flow-test-page";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
