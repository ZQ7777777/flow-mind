package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.api.dto.DirectSendContextDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ReadRecordDTO;
import com.flowmind.platform.api.dto.ReadRecordQuery;
import com.flowmind.platform.api.dto.StartedInstanceQuery;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.TodoTaskQuery;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.TaskQueryService;
import com.flowmind.platform.core.query.ReadRecordManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * M3 查询 REST 适配层。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@RestController
@Tag(name = "Platform Query", description = "Task and instance query APIs")
public class PlatformQueryController {

    private final TaskQueryService taskQueryService;
    private final ProcessRuntimeService processRuntimeService;
    private final ReadRecordManager readRecordManager;

    @Autowired
    public PlatformQueryController(TaskQueryService taskQueryService,
                                   ProcessRuntimeService processRuntimeService,
                                   ReadRecordManager readRecordManager) {
        this.taskQueryService = taskQueryService;
        this.processRuntimeService = processRuntimeService;
        this.readRecordManager = readRecordManager;
    }

    public PlatformQueryController(TaskQueryService taskQueryService,
                                   ProcessRuntimeService processRuntimeService) {
        this(taskQueryService, processRuntimeService, null);
    }

    @Operation(summary = "Query todo tasks")
    @GetMapping("/api/platform/tasks/todo")
    public PageResult<TaskDTO> queryTodoTasks(TodoTaskQuery query) {
        return taskQueryService.queryTodoTasks(query);
    }

    @Operation(summary = "Query completed tasks")
    @GetMapping("/api/platform/tasks/completed")
    public PageResult<HistoryTaskDTO> queryCompletedTasks(CompletedTaskQuery query) {
        return taskQueryService.queryCompletedTasks(query);
    }

    @Operation(summary = "Query started instances")
    @GetMapping("/api/platform/instances/started")
    public PageResult<ProcessInstanceDTO> queryStartedInstances(StartedInstanceQuery query) {
        return taskQueryService.queryStartedInstances(query);
    }

    @Operation(summary = "Query active tasks by instance")
    @GetMapping("/api/platform/instances/{instanceId}/active-tasks")
    public List<TaskDTO> queryActiveTasks(@Parameter(description = "Instance id") @PathVariable String instanceId) {
        return taskQueryService.queryActiveTasks(instanceId);
    }

    @Operation(summary = "Get trusted direct-send context")
    @GetMapping("/api/platform/tasks/{taskId}/direct-send-context")
    public DirectSendContextDTO getDirectSendContext(
            @Parameter(description = "Task id") @PathVariable String taskId) {
        return processRuntimeService.getDirectSendContext(taskId);
    }

    @Operation(summary = "Query history tasks by instance")
    @GetMapping("/api/platform/instances/{instanceId}/history-tasks")
    public List<HistoryTaskDTO> queryHistoryTasks(
            @Parameter(description = "Instance id") @PathVariable String instanceId) {
        return taskQueryService.queryHistoryTasks(instanceId);
    }

    @Operation(summary = "Query comments by instance")
    @GetMapping("/api/platform/instances/{instanceId}/comments")
    public List<ProcessCommentDTO> queryComments(
            @Parameter(description = "Instance id") @PathVariable String instanceId) {
        return taskQueryService.queryComments(instanceId);
    }

    @Operation(summary = "Mark instance as read")
    @PostMapping("/api/platform/instances/{instanceId}/read")
    public ReadRecordDTO markRead(@Parameter(description = "Instance id") @PathVariable String instanceId) {
        if (readRecordManager == null) {
            throw new UnsupportedOperationException("read record manager is unavailable");
        }
        return readRecordManager.markRead(instanceId);
    }

    @Operation(summary = "Query read records")
    @GetMapping("/api/platform/instances/{instanceId}/read-records")
    public PageResult<ReadRecordDTO> queryReadRecords(
            @Parameter(description = "Instance id") @PathVariable String instanceId,
            ReadRecordQuery query) {
        ReadRecordQuery normalized = query == null ? new ReadRecordQuery() : query;
        normalized.setInstanceId(instanceId);
        return taskQueryService.queryReadRecords(normalized);
    }

    @Operation(summary = "Get process instance")
    @GetMapping("/api/platform/instances/{instanceId}")
    public ProcessInstanceDTO getInstance(@Parameter(description = "Instance id") @PathVariable String instanceId) {
        return processRuntimeService.getInstance(instanceId);
    }
}
