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
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDetailDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.InstanceStatusEnum;
import com.flowmind.platform.api.enums.TaskStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowQueryServiceTest {

    @Test
    void completedAddsWithdrawContextOnlyToLatestEligibleHistoryRow() {
        PlatformFacade facade = mock(PlatformFacade.class);
        WorkflowQueryService service = new WorkflowQueryService(facade, new PlatformDtoMapper(),
                mock(WorkflowAccessGuard.class), new WorkflowAllowedActionResolver(new ObjectMapper(), facade));
        when(facade.currentUser()).thenReturn(new UserContext("sales01", "Sales", "dept-sales", "Sales"));
        HistoryTaskDTO old = history("old-history", "manager-old", ActionTypeEnum.APPROVE);
        HistoryTaskDTO source = history("apply-history", "sales01", ActionTypeEnum.SEND);
        old.setCompletedAt(LocalDateTime.of(2026, 8, 12, 8, 0));
        source.setCompletedAt(LocalDateTime.of(2026, 8, 12, 9, 0));
        PageResult<HistoryTaskDTO> page = new PageResult<HistoryTaskDTO>();
        page.setRecords(Arrays.asList(source, old)); page.setPageNo(1); page.setPageSize(20);
        page.setTotal(2L); page.setTotalPages(1);
        when(facade.completed(org.mockito.ArgumentMatchers.any(WorkflowListQuery.class))).thenReturn(page);
        ProcessInstanceDetailDTO instance = new ProcessInstanceDetailDTO();
        instance.setInstanceStatus(InstanceStatusEnum.RUNNING);
        instance.setActiveTasks(Collections.singletonList(task("manager-task", null,
                Collections.singletonList("manager01"))));
        instance.setHistoryTasks(Arrays.asList(old, source));
        when(facade.getInstance("instance-1")).thenReturn(instance);

        WorkflowPageResponse<com.flowmind.business.workflow.dto.WorkflowHistoryTaskResponse> result =
                service.completed(new WorkflowListQuery());

        assertThat(result.getRecords().get(0).getWithdrawContext()).isNotNull();
        assertThat(result.getRecords().get(0).getWithdrawContext().getTaskId()).isEqualTo("manager-task");
        assertThat(result.getRecords().get(1).getWithdrawContext()).isNull();
    }

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

    private HistoryTaskDTO history(String id, String userId, ActionTypeEnum action) {
        HistoryTaskDTO history = new HistoryTaskDTO(); history.setHistoryTaskId(id);
        history.setInstanceId("instance-1"); history.setActiveTaskId(id + "-task");
        history.setAssigneeUserId(userId); history.setActionType(action);
        history.setNodeCode("apply"); history.setNodeName("申请"); return history;
    }
}
