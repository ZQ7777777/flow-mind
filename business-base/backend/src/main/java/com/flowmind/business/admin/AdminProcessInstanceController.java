package com.flowmind.business.admin;

import com.flowmind.platform.api.dto.AdminHistoryTaskQuery;
import com.flowmind.platform.api.dto.AdminInstanceQuery;
import com.flowmind.platform.api.dto.AuditLogDTO;
import com.flowmind.platform.api.dto.AuditLogQuery;
import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.CallbackLogQuery;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.OperationResult;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.request.DeleteProcessInstanceRequest;
import com.flowmind.platform.api.request.TerminateProcessRequest;
import com.flowmind.platform.api.service.AdminProcessService;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.TaskQueryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Business-side administrator API for process-instance search and trace operations. */
@RestController
@RequestMapping("/api/admin/process-instances")
public class AdminProcessInstanceController {

    private final AdminProcessService adminProcessService;
    private final ProcessRuntimeService runtimeService;
    private final TaskQueryService taskQueryService;
    private final AdminAccessGuard accessGuard;

    public AdminProcessInstanceController(AdminProcessService adminProcessService,
                                          ProcessRuntimeService runtimeService,
                                          TaskQueryService taskQueryService,
                                          AdminAccessGuard accessGuard) {
        this.adminProcessService = adminProcessService;
        this.runtimeService = runtimeService;
        this.taskQueryService = taskQueryService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public PageResult<ProcessInstanceDTO> search(AdminInstanceQuery query) {
        accessGuard.requireAdministrator();
        return adminProcessService.queryInstances(query);
    }

    @GetMapping("/{instanceId}")
    public ProcessInstanceDetailDTO detail(@PathVariable String instanceId) {
        accessGuard.requireAdministrator();
        return runtimeService.getInstance(instanceId);
    }

    @GetMapping("/{instanceId}/active-tasks")
    public List<TaskDTO> activeTasks(@PathVariable String instanceId) {
        accessGuard.requireAdministrator();
        return taskQueryService.queryActiveTasks(instanceId);
    }

    @GetMapping("/{instanceId}/history-tasks")
    public PageResult<HistoryTaskDTO> historyTasks(@PathVariable String instanceId,
                                                   AdminHistoryTaskQuery query) {
        accessGuard.requireAdministrator();
        query.setInstanceId(instanceId);
        return adminProcessService.queryHistoryTasks(query);
    }

    @GetMapping("/{instanceId}/comments")
    public List<ProcessCommentDTO> comments(@PathVariable String instanceId) {
        accessGuard.requireAdministrator();
        return taskQueryService.queryComments(instanceId);
    }

    @GetMapping("/{instanceId}/callback-logs")
    public PageResult<CallbackLogDTO> callbackLogs(@PathVariable String instanceId,
                                                   CallbackLogQuery query) {
        accessGuard.requireAdministrator();
        query.setInstanceId(instanceId);
        return adminProcessService.queryCallbackLogs(query);
    }

    @GetMapping("/{instanceId}/read-records")
    public PageResult<ReadRecordDTO> readRecords(@PathVariable String instanceId,
                                                 ReadRecordQuery query) {
        accessGuard.requireAdministrator();
        query.setInstanceId(instanceId);
        return taskQueryService.queryReadRecords(query);
    }

    @GetMapping("/{instanceId}/audit-logs")
    public PageResult<AuditLogDTO> auditLogs(@PathVariable String instanceId,
                                             AuditLogQuery query) {
        accessGuard.requireAdministrator();
        query.setInstanceId(instanceId);
        return adminProcessService.queryAuditLogs(query);
    }

    @PostMapping("/{instanceId}/terminate")
    public ProcessInstanceDTO terminate(@PathVariable String instanceId,
                                        @RequestBody TerminateProcessRequest request) {
        request.setInstanceId(instanceId);
        request.setOperatorUserId(accessGuard.requireAdministrator());
        return runtimeService.terminate(request);
    }

    @DeleteMapping("/{instanceId}")
    public OperationResult delete(@PathVariable String instanceId,
                                  @RequestBody DeleteProcessInstanceRequest request) {
        request.setInstanceId(instanceId);
        request.setOperatorUserId(accessGuard.requireAdministrator());
        return runtimeService.deleteInstance(request);
    }
}
