package com.flowmind.business.admin;

import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.enums.AttachmentTemplateStatusEnum;
import com.flowmind.platform.core.definition.ProcessAttachmentTemplateManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Administrator API for attachment-template versions used by the definition editor. */
@RestController
@RequestMapping("/api/admin/attachment-templates")
public class AdminAttachmentTemplateController {

    private final ProcessAttachmentTemplateManager templateManager;
    private final AdminAccessGuard accessGuard;

    public AdminAttachmentTemplateController(ProcessAttachmentTemplateManager templateManager,
                                             AdminAccessGuard accessGuard) {
        this.templateManager = templateManager;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public List<ProcessAttachmentTemplateDTO> search(
            @RequestParam(required = false) String attachmentCode,
            @RequestParam(required = false) String templateStatus,
            @RequestParam(required = false) Integer templateVersion) {
        accessGuard.requireAdministrator();
        AttachmentTemplateStatusEnum status = hasText(templateStatus)
                ? AttachmentTemplateStatusEnum.valueOf(templateStatus.trim()) : null;
        return templateManager.findTemplates(attachmentCode, status, templateVersion);
    }

    @PostMapping
    public ProcessAttachmentTemplateDTO create(@RequestBody ProcessAttachmentTemplateDTO request) {
        return templateManager.createTemplateVersion(request, accessGuard.requireAdministrator());
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
