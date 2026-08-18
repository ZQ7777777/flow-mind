package com.flowmind.business.entry;

import com.flowmind.business.entry.dto.ProcessEntryLinkResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务大厅流程录入页入口查询接口。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@RestController
@RequestMapping("/api/workflow/process-entry-links")
public class ProcessEntryLinkController {

    private final BusinessEntryConfigService service;

    public ProcessEntryLinkController(BusinessEntryConfigService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProcessEntryLinkResponse> list() {
        return service.listVisibleEntries();
    }

    @GetMapping("/{definitionId}")
    public ProcessEntryLinkResponse detail(@PathVariable String definitionId) {
        return service.visibleEntry(definitionId);
    }
}
