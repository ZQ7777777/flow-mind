package com.flowmind.business.agent;

import com.flowmind.business.security.CurrentBusinessUserProvider;
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

/**
 * agent-web 写入 business-base 内嵌 Platform 附件模板库的受控桥接入口。
 * 附件模板决策归属 agent-web 工作流；business-base 只通过平台服务持久化。
 */
@RestController
@RequestMapping("/api/platform/attachment-templates")
public class AgentPlatformAttachmentTemplateBridgeController {

    private final ProcessAttachmentTemplateManager manager;
    private final CurrentBusinessUserProvider currentUserProvider;

    public AgentPlatformAttachmentTemplateBridgeController(ProcessAttachmentTemplateManager manager,
                                                                 CurrentBusinessUserProvider currentUserProvider) {
        this.manager = manager;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ProcessAttachmentTemplateDTO createTemplate(@RequestBody ProcessAttachmentTemplateDTO request) {
        return manager.createTemplateVersion(request, currentUserProvider.currentUser().getUserId());
    }

    @GetMapping
    public List<ProcessAttachmentTemplateDTO> findTemplates(
            @RequestParam(required = false) String attachmentCode,
            @RequestParam(required = false) String templateStatus,
            @RequestParam(required = false) Integer templateVersion) {
        AttachmentTemplateStatusEnum status = templateStatus == null || templateStatus.trim().isEmpty()
                ? null : AttachmentTemplateStatusEnum.valueOf(templateStatus.trim());
        return manager.findTemplates(attachmentCode, status, templateVersion);
    }
}
