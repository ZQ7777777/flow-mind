package com.flowmind.business.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator organization options API for process-definition editing. */
@RestController
@RequestMapping("/api/admin/process-definition-options")
public class ProcessDefinitionOptionsController {
    private final ProcessDefinitionOptionsService optionsService;
    private final AdminAccessGuard accessGuard;

    public ProcessDefinitionOptionsController(ProcessDefinitionOptionsService optionsService,
                                              AdminAccessGuard accessGuard) {
        this.optionsService = optionsService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public ProcessDefinitionOptionsResponse options() {
        accessGuard.requireAdministrator();
        return optionsService.options();
    }
}
