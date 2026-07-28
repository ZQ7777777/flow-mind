package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.AlertDTO;
import com.flowmind.platform.api.dto.AlertQuery;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ReminderDTO;
import com.flowmind.platform.api.dto.ReminderQuery;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.request.HandleAlertRequest;
import com.flowmind.platform.api.request.RemindTaskRequest;
import com.flowmind.platform.api.request.TimeoutScanRequest;
import com.flowmind.platform.api.service.ProcessMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** M5 monitor REST endpoints. */
@RestController
@Tag(name = "Platform Monitor", description = "Reminder, timeout scan and alert APIs")
public class ProcessMonitorController {

    private final ProcessMonitorService monitorService;

    public ProcessMonitorController(ProcessMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Operation(summary = "Remind task")
    @PostMapping("/api/platform/tasks/{taskId}/remind")
    public ReminderDTO remindTask(@Parameter(description = "Task id") @PathVariable String taskId,
                                  @RequestBody RemindTaskRequest request) {
        request.setTaskId(taskId);
        return monitorService.remindTask(request);
    }

    @Operation(summary = "Query reminders")
    @GetMapping("/api/platform/reminders")
    public PageResult<ReminderDTO> queryReminders(ReminderQuery query) {
        return monitorService.queryReminders(query);
    }

    @Operation(summary = "Scan timeout tasks")
    @PostMapping("/api/platform/admin/timeout-scan")
    public List<TaskDTO> scanTimeoutTasks(@RequestBody TimeoutScanRequest request) {
        return monitorService.scanTimeoutTasks(request);
    }

    @Operation(summary = "Query alerts")
    @GetMapping("/api/platform/admin/alerts")
    public PageResult<AlertDTO> queryAlerts(AlertQuery query) {
        return monitorService.queryAlerts(query);
    }

    @Operation(summary = "Handle alert")
    @PostMapping("/api/platform/admin/alerts/{alertId}/handle")
    public AlertDTO handleAlert(@Parameter(description = "Alert id") @PathVariable String alertId,
                                @RequestBody HandleAlertRequest request) {
        request.setAlertId(alertId);
        return monitorService.handleAlert(request);
    }
}
