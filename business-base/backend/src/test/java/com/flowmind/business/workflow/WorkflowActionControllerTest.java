package com.flowmind.business.workflow;

import com.flowmind.business.common.BusinessExceptionHandler;
import com.flowmind.business.common.RequestIdFilter;
import com.flowmind.business.workflow.dto.WorkflowActionRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowActionControllerTest {
    private MockMvc mockMvc;
    private WorkflowActionService actionService;

    @BeforeEach
    void setUp() {
        actionService = mock(WorkflowActionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkflowActionController(actionService))
                .setControllerAdvice(new BusinessExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }

    @Test
    void rejectsMissingIdempotencyKeyAndTaskVersion() throws Exception {
        mockMvc.perform(post("/api/workflow/tasks/task-1/approve")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedTaskVersion\":1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/workflow/tasks/task-1/approve").header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void directSendDoesNotRequireTargetNodeCode() throws Exception {
        mockMvc.perform(post("/api/workflow/tasks/task-1/direct-send")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedTaskVersion\":3}"))
                .andExpect(status().isOk());

        verify(actionService).execute(org.mockito.ArgumentMatchers.eq("direct-send"),
                org.mockito.ArgumentMatchers.eq("task-1"), org.mockito.ArgumentMatchers.eq("key-1"),
                org.mockito.ArgumentMatchers.any(WorkflowActionRequests.DirectSend.class));
    }
}
