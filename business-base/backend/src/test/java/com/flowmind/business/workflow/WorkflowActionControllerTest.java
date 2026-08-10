package com.flowmind.business.workflow;

import com.flowmind.business.common.BusinessExceptionHandler;
import com.flowmind.business.common.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowActionControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkflowActionController(mock(WorkflowActionService.class)))
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
}
