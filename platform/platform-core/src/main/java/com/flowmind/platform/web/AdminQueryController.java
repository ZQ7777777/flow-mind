package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.AuditLogDTO;
import com.flowmind.platform.api.dto.AuditLogQuery;
import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.AdminHistoryTaskQuery;
import com.flowmind.platform.api.dto.AdminInstanceQuery;
import com.flowmind.platform.api.dto.AdminTaskQuery;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.TaskGroupViewDTO;
import com.flowmind.platform.api.service.AdminProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** M5 admin query REST endpoints. */
@RestController
@Tag(name = "Platform Admin Query", description = "Audit and callback query APIs")
public class AdminQueryController {

    private final AdminProcessService adminProcessService;

    public AdminQueryController(AdminProcessService adminProcessService) {
        this.adminProcessService = adminProcessService;
    }

    @Operation(summary = "Query process instances for admin")
    @GetMapping("/api/platform/admin/instances")
    public PageResult<ProcessInstanceDTO> queryInstances(AdminInstanceQuery query) {
        return adminProcessService.queryInstances(query);
    }

    @Operation(summary = "Query active tasks for admin")
    @GetMapping("/api/platform/admin/tasks")
    public PageResult<TaskDTO> queryActiveTasks(AdminTaskQuery query) {
        return adminProcessService.queryActiveTasks(query);
    }

    @Operation(summary = "Query history tasks for admin")
    @GetMapping("/api/platform/admin/history-tasks")
    public PageResult<HistoryTaskDTO> queryHistoryTasks(AdminHistoryTaskQuery query) {
        return adminProcessService.queryHistoryTasks(query);
    }

    @Operation(summary = "Query task groups for admin diagnostics")
    @GetMapping("/api/platform/admin/instances/{instanceId}/task-groups")
    public List<TaskGroupViewDTO> queryTaskGroups(@Parameter(description = "Instance id")
                                                  @PathVariable String instanceId) {
        return adminProcessService.queryTaskGroups(instanceId);
    }

    @Operation(summary = "Query audit logs")
    @GetMapping("/api/platform/admin/audit-logs")
    public PageResult<AuditLogDTO> queryAuditLogs(AuditLogQuery query) {
        return adminProcessService.queryAuditLogs(query);
    }

    @Operation(summary = "Query callback logs")
    @GetMapping("/api/platform/admin/callback-logs")
    public PageResult<CallbackLogDTO> queryCallbackLogs(CallbackLogQuery query) {
        return adminProcessService.queryCallbackLogs(query);
    }
}
