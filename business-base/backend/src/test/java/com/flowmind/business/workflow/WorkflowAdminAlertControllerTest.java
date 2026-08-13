package com.flowmind.business.workflow;

import com.flowmind.platform.api.dto.AlertDTO;
import com.flowmind.platform.api.enums.AlertStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowAdminAlertControllerTest {

    private WorkflowAdminAlertService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(WorkflowAdminAlertService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkflowAdminAlertController(service)).build();
    }

    @Test
    void exposesAlertQueryEndpoint() throws Exception {
        com.flowmind.business.workflow.dto.WorkflowPageResponse<AlertDTO> page = new com.flowmind.business.workflow.dto.WorkflowPageResponse<AlertDTO>();
        AlertDTO alert = new AlertDTO();
        alert.setAlertId("alert-1");
        alert.setAlertStatus(AlertStatusEnum.OPEN);
        page.setRecords(java.util.Collections.singletonList(alert));
        page.setPageNo(1);
        page.setPageSize(20);
        page.setTotal(1L);
        page.setTotalPages(1);
        when(service.query(any(com.flowmind.platform.api.dto.AlertQuery.class))).thenReturn(page);

        mockMvc.perform(get("/api/workflow/admin/alerts")
                        .param("alertStatus", "OPEN")
                        .param("pageNo", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].alertId").value("alert-1"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void exposesHandleAlertEndpointWithIdempotencyKey() throws Exception {
        AlertDTO alert = new AlertDTO();
        alert.setAlertId("alert-1");
        alert.setAlertStatus(AlertStatusEnum.HANDLED);
        when(service.handle(any(String.class), any(String.class), any(com.flowmind.platform.api.request.HandleAlertRequest.class)))
                .thenReturn(alert);

        mockMvc.perform(post("/api/workflow/admin/alerts/alert-1/handle")
                        .header("Idempotency-Key", "retry-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"HANDLED\",\"comment\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value("alert-1"))
                .andExpect(jsonPath("$.alertStatus").value("HANDLED"));

        ArgumentCaptor<com.flowmind.platform.api.request.HandleAlertRequest> captor =
                ArgumentCaptor.forClass(com.flowmind.platform.api.request.HandleAlertRequest.class);
        verify(service).handle(org.mockito.Mockito.eq("alert-1"), org.mockito.Mockito.eq("retry-key"), captor.capture());
        assertThat(captor.getValue().getTargetStatus()).isEqualTo(AlertStatusEnum.HANDLED);
        assertThat(captor.getValue().getComment()).isEqualTo("done");
    }
}