package com.flowmind.business.workflow;

import com.flowmind.business.workflow.dto.WorkflowStartableProcessResponse;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowStartableProcessServiceTest {

    @Test
    void returnsActiveEntryApplicationProcessNameFromDefinitionService() {
        ProcessDefinitionService definitionService = mock(ProcessDefinitionService.class);
        ProcessDefinitionDTO definition = new ProcessDefinitionDTO();
        definition.setProcessCode("entry_application");
        definition.setProcessName("客户入金");
        PageResult<ProcessDefinitionDTO> page = new PageResult<ProcessDefinitionDTO>();
        page.setRecords(Collections.singletonList(definition));
        when(definitionService.searchDefinitions(org.mockito.Mockito.any(ProcessDefinitionQuery.class)))
                .thenReturn(page);

        WorkflowStartableProcessResponse response =
                new WorkflowStartableProcessService(definitionService).entryApplication();

        assertThat(response.getProcessCode()).isEqualTo("entry_application");
        assertThat(response.getProcessName()).isEqualTo("客户入金");
        ArgumentCaptor<ProcessDefinitionQuery> query = ArgumentCaptor.forClass(ProcessDefinitionQuery.class);
        org.mockito.Mockito.verify(definitionService).searchDefinitions(query.capture());
        assertThat(query.getValue().getProcessCode()).isEqualTo("entry_application");
        assertThat(query.getValue().getDefinitionStatus()).isEqualTo(DefinitionStatusEnum.PUBLISHED);
        assertThat(query.getValue().getActivationStatus()).isEqualTo(ActivationStatusEnum.ACTIVE);
    }
}
