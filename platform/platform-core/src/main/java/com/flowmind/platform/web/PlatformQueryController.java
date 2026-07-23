package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.StartedInstanceQuery;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.TodoTaskQuery;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.TaskQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * M3 查询 REST 适配层。
 *
 * @author FlowMind
 * @since 2026-07-23
 */
@RestController
public class PlatformQueryController {

    private final TaskQueryService taskQueryService;
    private final ProcessRuntimeService processRuntimeService;

    public PlatformQueryController(TaskQueryService taskQueryService,
                                   ProcessRuntimeService processRuntimeService) {
        this.taskQueryService = taskQueryService;
        this.processRuntimeService = processRuntimeService;
    }

    @GetMapping("/api/platform/tasks/todo")
    public PageResult<TaskDTO> queryTodoTasks(TodoTaskQuery query) {
        return taskQueryService.queryTodoTasks(query);
    }

    @GetMapping("/api/platform/tasks/completed")
    public PageResult<HistoryTaskDTO> queryCompletedTasks(CompletedTaskQuery query) {
        return taskQueryService.queryCompletedTasks(query);
    }

    @GetMapping("/api/platform/instances/started")
    public PageResult<ProcessInstanceDTO> queryStartedInstances(StartedInstanceQuery query) {
        return taskQueryService.queryStartedInstances(query);
    }

    @GetMapping("/api/platform/instances/{instanceId}/active-tasks")
    public List<TaskDTO> queryActiveTasks(@PathVariable String instanceId) {
        return taskQueryService.queryActiveTasks(instanceId);
    }

    @GetMapping("/api/platform/instances/{instanceId}/history-tasks")
    public List<HistoryTaskDTO> queryHistoryTasks(@PathVariable String instanceId) {
        return taskQueryService.queryHistoryTasks(instanceId);
    }

    @GetMapping("/api/platform/instances/{instanceId}/comments")
    public List<ProcessCommentDTO> queryComments(@PathVariable String instanceId) {
        return taskQueryService.queryComments(instanceId);
    }

    @GetMapping("/api/platform/instances/{instanceId}")
    public ProcessInstanceDTO getInstance(@PathVariable String instanceId) {
        return processRuntimeService.getInstance(instanceId);
    }
}
