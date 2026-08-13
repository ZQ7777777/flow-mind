package com.flowmind.business.workflow;

import com.flowmind.business.workflow.dto.WorkflowStartableProcessResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow/startable-processes")
public class WorkflowStartableProcessController {
    private final WorkflowStartableProcessService startableProcessService;

    public WorkflowStartableProcessController(WorkflowStartableProcessService startableProcessService) {
        this.startableProcessService = startableProcessService;
    }

    @GetMapping("/entry-application")
    public WorkflowStartableProcessResponse entryApplication() {
        return startableProcessService.entryApplication();
    }
}
