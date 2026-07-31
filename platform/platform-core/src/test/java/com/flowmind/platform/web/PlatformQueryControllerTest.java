package com.flowmind.platform.web;

import com.flowmind.platform.api.dto.CompletedTaskQuery;
import com.flowmind.platform.api.dto.DirectSendContextDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessCommentDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.StartedInstanceQuery;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.TodoTaskQuery;
import com.flowmind.platform.api.service.ProcessRuntimeService;
import com.flowmind.platform.api.service.TaskQueryService;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformQueryControllerTest {

    @Test
    void queryEndpointsDelegateToServices() {
        TaskQueryService taskQueryService = mock(TaskQueryService.class);
        ProcessRuntimeService runtimeService = mock(ProcessRuntimeService.class);
        PlatformQueryController controller = new PlatformQueryController(taskQueryService, runtimeService);
        TodoTaskQuery todoQuery = new TodoTaskQuery();
        CompletedTaskQuery completedQuery = new CompletedTaskQuery();
        StartedInstanceQuery startedQuery = new StartedInstanceQuery();
        PageResult<TaskDTO> todoResult = new PageResult<TaskDTO>();
        PageResult<HistoryTaskDTO> completedResult = new PageResult<HistoryTaskDTO>();
        PageResult<ProcessInstanceDTO> startedResult = new PageResult<ProcessInstanceDTO>();
        ProcessInstanceDetailDTO detail = new ProcessInstanceDetailDTO();
        detail.setInstanceId("instance-1");
        DirectSendContextDTO directSendContext = new DirectSendContextDTO();
        directSendContext.setTaskId("task-1");
        directSendContext.setAllowed(true);
        directSendContext.setTargetNodeCode("finance");
        when(taskQueryService.queryTodoTasks(todoQuery)).thenReturn(todoResult);
        when(taskQueryService.queryCompletedTasks(completedQuery)).thenReturn(completedResult);
        when(taskQueryService.queryStartedInstances(startedQuery)).thenReturn(startedResult);
        when(taskQueryService.queryActiveTasks("instance-1")).thenReturn(Collections.<TaskDTO>emptyList());
        when(taskQueryService.queryHistoryTasks("instance-1")).thenReturn(Collections.<HistoryTaskDTO>emptyList());
        when(taskQueryService.queryComments("instance-1")).thenReturn(Collections.<ProcessCommentDTO>emptyList());
        when(runtimeService.getInstance("instance-1")).thenReturn(detail);
        when(runtimeService.getDirectSendContext("task-1")).thenReturn(directSendContext);

        assertEquals(todoResult, controller.queryTodoTasks(todoQuery));
        assertEquals(completedResult, controller.queryCompletedTasks(completedQuery));
        assertEquals(startedResult, controller.queryStartedInstances(startedQuery));
        assertEquals(0, controller.queryActiveTasks("instance-1").size());
        assertEquals(0, controller.queryHistoryTasks("instance-1").size());
        assertEquals(0, controller.queryComments("instance-1").size());
        assertEquals("instance-1", controller.getInstance("instance-1").getInstanceId());
        assertEquals("finance", controller.getDirectSendContext("task-1").getTargetNodeCode());

        verify(taskQueryService).queryComments("instance-1");
        verify(runtimeService).getDirectSendContext("task-1");
    }
}
