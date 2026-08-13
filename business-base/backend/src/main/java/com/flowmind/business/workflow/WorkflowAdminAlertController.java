package com.flowmind.business.workflow;

import com.flowmind.business.workflow.dto.WorkflowPageResponse;
import com.flowmind.platform.api.dto.AlertDTO;
import com.flowmind.platform.api.dto.AlertQuery;
import com.flowmind.platform.api.request.HandleAlertRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow/admin/alerts")
public class WorkflowAdminAlertController {
    private final WorkflowAdminAlertService alertService;

    public WorkflowAdminAlertController(WorkflowAdminAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public WorkflowPageResponse<AlertDTO> query(AlertQuery query) {
        return alertService.query(query);
    }

    @PostMapping("/{alertId}/handle")
    public AlertDTO handle(@PathVariable String alertId,
                           @RequestHeader("Idempotency-Key") String key,
                           @RequestBody HandleAlertRequest body) {
        return alertService.handle(alertId, key, body);
    }
}