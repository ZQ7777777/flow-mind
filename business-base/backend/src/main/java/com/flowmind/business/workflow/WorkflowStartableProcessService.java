package com.flowmind.business.workflow;

import com.flowmind.business.workflow.dto.WorkflowStartableProcessResponse;
import com.flowmind.platform.api.dto.PageResult;
import com.flowmind.platform.api.dto.ProcessDefinitionDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.service.ProcessDefinitionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkflowStartableProcessService {
    static final String ENTRY_APPLICATION_PROCESS_CODE = "entry_application";

    private final ProcessDefinitionService definitionService;

    public WorkflowStartableProcessService(ProcessDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    public WorkflowStartableProcessResponse entryApplication() {
        ProcessDefinitionQuery query = new ProcessDefinitionQuery();
        query.setPageNo(1);
        query.setPageSize(1);
        query.setProcessCode(ENTRY_APPLICATION_PROCESS_CODE);
        query.setDefinitionStatus(DefinitionStatusEnum.PUBLISHED);
        query.setActivationStatus(ActivationStatusEnum.ACTIVE);
        PageResult<ProcessDefinitionDTO> page = definitionService.searchDefinitions(query);
        List<ProcessDefinitionDTO> records = page == null ? null : page.getRecords();
        if (records == null || records.isEmpty()) {
            return new WorkflowStartableProcessResponse(ENTRY_APPLICATION_PROCESS_CODE, ENTRY_APPLICATION_PROCESS_CODE);
        }
        ProcessDefinitionDTO definition = records.get(0);
        String processName = definition.getProcessName() == null || definition.getProcessName().trim().isEmpty()
                ? definition.getProcessCode()
                : definition.getProcessName();
        return new WorkflowStartableProcessResponse(definition.getProcessCode(), processName);
    }
}
