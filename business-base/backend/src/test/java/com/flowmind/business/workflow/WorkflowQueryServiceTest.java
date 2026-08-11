package com.flowmind.business.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.business.platform.PlatformDtoMapper;
import com.flowmind.business.platform.PlatformFacade;
import com.flowmind.business.security.WorkflowAccessGuard;
import com.flowmind.business.workflow.dto.WorkflowListQuery;
import com.flowmind.business.workflow.dto.WorkflowPageResponse;
import com.flowmind.business.workflow.dto.WorkflowTaskResponse;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowQueryServiceTest {

    @Test
    void todoDropsTasksWithoutExecutableActionsForCurrentUser() {
        PlatformFacade facade = mock(PlatformFacade.class);
        WorkflowAllowedActionResolver resolver = new WorkflowAllowedActionResolver(
                new ObjectMapper(), facade);
        WorkflowQueryService service = new WorkflowQueryService(facade, new PlatformDtoMapper(),
                mock(WorkflowAccessGuard.class), resolver);
        when(facade.currentUser()).thenReturn(new UserContext("finance02", "Finance 02", "dept-finance", "Finance"));
        PageResult<TaskDTO> page = new PageResult<TaskDTO>();
        page.setPageNo(Integer.valueOf(1));
        page.setPageSize(Integer.valueOf(20));
        page.setTotal(Long.valueOf(2));
        page.setTotalPages(Integer.valueOf(1));
        page.setRecords(Arrays.asList(
                task("task-claimed-by-finance01", "finance01", Arrays.asList("finance01", "finance02")),
                task("task-open-for-finance02", null, Collections.singletonList("finance02"))));
        when(facade.todo(org.mockito.ArgumentMatchers.any(WorkflowListQuery.class))).thenReturn(page);

        WorkflowPageResponse<WorkflowTaskResponse> result = service.todo(new WorkflowListQuery());

        assertThat(result.getRecords()).extracting(WorkflowTaskResponse::getTaskId)
                .containsExactly("task-open-for-finance02");
        assertThat(result.getTotal()).isEqualTo(1L);
    }

    private TaskDTO task(String taskId, String assigneeUserId, java.util.List<String> candidates) {
        TaskDTO task = new TaskDTO();
        task.setTaskId(taskId);
        task.setInstanceId("instance-1");
        task.setNodeCode("finance-review");
        task.setInstanceTitle(taskId);
        task.setTaskStatus(assigneeUserId == null ? TaskStatusEnum.ACTIVE : TaskStatusEnum.CLAIMED);
        task.setAssigneeUserId(assigneeUserId);
        task.setCandidateUserIds(candidates);
        task.setTaskVersion(Long.valueOf(1));
        return task;
    }
}