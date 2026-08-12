package com.flowmind.business.platform;

import com.flowmind.business.workflow.dto.WorkflowDetailResponse;
import com.flowmind.platform.api.dto.AttachmentDTO;
import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessInstanceDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.NodeTypeEnum;
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
        definition.setAttachmentTemplates(Arrays.asList(
                attachmentTemplate("managerNote", "审批补充材料", "manager", 2),
                attachmentTemplate("financeVoucher", "财务凭证", "finance", 1)));
        com.flowmind.platform.api.dto.TaskDTO currentTask = new com.flowmind.platform.api.dto.TaskDTO();
        currentTask.setTaskId("task-1");
        currentTask.setNodeCode("manager");
        HistoryTaskDTO history = new HistoryTaskDTO();
        history.setOperationId("internal-operation"); history.setVariablesSnapshot(variables);
        AttachmentDTO attachment = new AttachmentDTO();
        attachment.setAttachmentId("attachment-1"); attachment.setFileName("receipt.pdf");
        attachment.setStorageKey("must-not-leak"); attachment.setDeleted(Boolean.FALSE);
        ProcessNodeDTO rejectTarget = new ProcessNodeDTO();
        rejectTarget.setNodeCode("apply"); rejectTarget.setNodeName("鐢宠");
        rejectTarget.setNodeType(NodeTypeEnum.USER_TASK);
        rejectTarget.setListenerConfig("must-not-leak");

        WorkflowDetailResponse result = new PlatformDtoMapper().detail(instance, definition, currentTask,
                Collections.emptyList(), Collections.singletonList(history), Collections.emptyList(),
                Collections.singletonList(attachment), Collections.singletonList(rejectTarget),
                Collections.emptyList(), Collections.emptyList());

        assertThat(result.getFormFields()).extracting(WorkflowDetailResponse.FormFieldView::getFieldCode)
                .containsExactly("amount", "currency");
        assertThat(result.getInstance().getVariables()).containsOnlyKeys("amount", "currency");
        assertThat(result.getAttachments()).extracting(WorkflowDetailResponse.AttachmentView::getFileName)
                .containsExactly("receipt.pdf");
        assertThat(result.getUploadableAttachments())
                .extracting(WorkflowDetailResponse.UploadableAttachmentView::getAttachmentName)
                .containsExactly("审批补充材料");
        assertThat(result.getUploadableAttachments().get(0).getAttachmentCode()).isEqualTo("managerNote");
        assertThat(result.getUploadableAttachments().get(0).getFieldCode()).isNull();
        assertThat(result.getRejectTargetNodes()).extracting(WorkflowDetailResponse.NodeView::getNodeCode)
                .containsExactly("apply");
        assertThat(result.getRejectTargetNodes()).extracting(WorkflowDetailResponse.NodeView::getNodeName)
                .containsExactly("鐢宠");
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

    private ProcessAttachmentTemplateDTO attachmentTemplate(String code, String name, String nodeCode, int order) {
        ProcessAttachmentTemplateDTO template = new ProcessAttachmentTemplateDTO();
        template.setAttachmentCode(code);
        template.setAttachmentName(name);
        template.setDescription(name + "说明");
        template.setApplicableNodeCodes(Collections.singletonList(nodeCode));
        template.setAllowedExtensions(Collections.singletonList("pdf"));
        template.setSortOrder(order);
        return template;
    }
}
