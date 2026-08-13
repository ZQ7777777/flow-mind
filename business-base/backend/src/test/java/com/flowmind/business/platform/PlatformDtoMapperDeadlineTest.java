package com.flowmind.business.platform;

import com.flowmind.business.workflow.dto.WorkflowTaskResponse;
import com.flowmind.platform.api.dto.TaskDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformDtoMapperDeadlineTest {

    private final PlatformDtoMapper mapper = new PlatformDtoMapper();

    @Test
    void taskDeadlineStatusReflectsDueAt() {
        LocalDateTime now = LocalDateTime.now();

        WorkflowTaskResponse overdue = mapper.task(task("overdue", now.minusMinutes(1)));
        WorkflowTaskResponse dueSoon = mapper.task(task("soon", now.plusMinutes(10)));
        WorkflowTaskResponse normal = mapper.task(task("normal", now.plusHours(2)));

        assertThat(overdue.getDeadlineStatus()).isEqualTo("OVERDUE");
        assertThat(dueSoon.getDeadlineStatus()).isEqualTo("DUE_SOON");
        assertThat(normal.getDeadlineStatus()).isEqualTo("NORMAL");
    }

    private TaskDTO task(String taskId, LocalDateTime dueAt) {
        TaskDTO task = new TaskDTO();
        task.setTaskId(taskId);
        task.setDueAt(dueAt);
        return task;
    }
}