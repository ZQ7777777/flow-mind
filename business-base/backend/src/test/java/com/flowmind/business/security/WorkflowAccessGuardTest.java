package com.flowmind.business.security;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowAccessGuardTest {

    @Test
    void permitsEveryFrozenParticipantTypeAndRejectsOutsiders() {
        WorkflowAccessGuard guard = new WorkflowAccessGuard(userId -> "admin".equals(userId));
        ProcessInstanceDTO instance = new ProcessInstanceDTO();
        instance.setStarterUserId("starter");
        TaskDTO task = new TaskDTO();
        task.setCandidateUserIds(Collections.singletonList("candidate"));
        task.setAssigneeUserId("delegate");
        task.setDelegateFromUserId("principal");
        HistoryTaskDTO history = new HistoryTaskDTO();
        history.setAssigneeUserId("historical-handler");

        assertDoesNotThrow(() -> guard.check(instance, Collections.singletonList(task),
                Collections.singletonList(history), "starter"));
        assertDoesNotThrow(() -> guard.check(instance, Collections.singletonList(task),
                Collections.singletonList(history), "candidate"));
        assertDoesNotThrow(() -> guard.check(instance, Collections.singletonList(task),
                Collections.singletonList(history), "delegate"));
        assertDoesNotThrow(() -> guard.check(instance, Collections.singletonList(task),
                Collections.singletonList(history), "historical-handler"));
        assertDoesNotThrow(() -> guard.check(instance, Collections.singletonList(task),
                Collections.singletonList(history), "admin"));
        assertThrows(BusinessAccessDeniedException.class, () -> guard.check(instance,
                Collections.singletonList(task), Collections.singletonList(history), "outsider"));
    }
}
