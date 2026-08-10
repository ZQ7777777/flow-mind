package com.flowmind.business.platform;

import com.flowmind.business.workflow.dto.WorkflowDetailResponse;
import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformDtoMapperTest {

    @Test
    void detailSortsFieldsFiltersVariablesAndRemovesInternalAttachmentData() {
        ProcessInstanceDTO instance = new ProcessInstanceDTO();
        instance.setInstanceId("instance-1");
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("systemApprover", "secret"); variables.put("amount", 1000); variables.put("currency", "CNY");
        instance.setVariables(variables);
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setProcessCode("entry"); definition.setProcessName("入金申请"); definition.setVersion(1);
        definition.setFormFields(Arrays.asList(field("currency", 2), field("amount", 1)));
        HistoryTaskDTO history = new HistoryTaskDTO();
        history.setOperationId("internal-operation"); history.setVariablesSnapshot(variables);
        AttachmentDTO attachment = new AttachmentDTO();
        attachment.setAttachmentId("attachment-1"); attachment.setFileName("receipt.pdf");
        attachment.setStorageKey("must-not-leak"); attachment.setDeleted(Boolean.FALSE);

        WorkflowDetailResponse result = new PlatformDtoMapper().detail(instance, definition, null,
                Collections.emptyList(), Collections.singletonList(history), Collections.emptyList(),
                Collections.singletonList(attachment), Collections.emptyList());

        assertThat(result.getFormFields()).extracting(WorkflowDetailResponse.FormFieldView::getFieldCode)
                .containsExactly("amount", "currency");
        assertThat(result.getInstance().getVariables()).containsOnlyKeys("amount", "currency");
        assertThat(result.getAttachments()).extracting(WorkflowDetailResponse.AttachmentView::getFileName)
                .containsExactly("receipt.pdf");
        assertThat(WorkflowDetailResponse.AttachmentView.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("storageKey"));
        assertThat(com.flowmind.business.workflow.dto.WorkflowHistoryTaskResponse.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("variablesSnapshot") || field.getName().equals("operationId"));
    }

    private ProcessFormFieldDTO field(String code, int order) {
        ProcessFormFieldDTO field = new ProcessFormFieldDTO();
        field.setFieldCode(code); field.setFieldName(code); field.setSortOrder(order);
        return field;
    }
}
