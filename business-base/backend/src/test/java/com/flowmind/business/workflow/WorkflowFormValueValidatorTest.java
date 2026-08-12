package com.flowmind.business.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowFormValueValidatorTest {
    private final WorkflowFormValueValidator validator = new WorkflowFormValueValidator(new ObjectMapper());

    @Test
    void acceptsDeclaredStarterFieldsAndPreservesDefinitionOrder() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("currency", "CNY"); values.put("amount", 10.5);

        Map<String, Object> result = validator.validate(definition(), task(), values);

        assertThat(result.keySet()).containsExactly("amount", "currency");
    }

    @Test
    void rejectsUnknownMissingInvalidAndNonStarterValues() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("amount", 0); values.put("currency", "CNY"); values.put("systemStatus", "RUNNING");
        assertThatThrownBy(() -> validator.validate(definition(), task(), values))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("systemStatus");

        values.remove("systemStatus"); values.remove("currency"); values.put("amount", 10);
        assertThatThrownBy(() -> validator.validate(definition(), task(), values))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("currency");

        values.put("currency", "GBP");
        assertThatThrownBy(() -> validator.validate(definition(), task(), values))
                .isInstanceOf(IllegalArgumentException.class);

        ProcessDefinitionDetailDTO nonStarter = definition();
        nonStarter.getNodes().get(0).setApproverRuleType(ApproverRuleTypeEnum.USER);
        values.put("currency", "CNY"); values.put("amount", 10);
        assertThatThrownBy(() -> validator.validate(nonStarter, task(), values))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("申请返工节点");
    }

    private ProcessDefinitionDetailDTO definition() {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        ProcessNodeDTO node = new ProcessNodeDTO(); node.setNodeCode("apply");
        node.setApproverRuleType(ApproverRuleTypeEnum.STARTER); definition.setNodes(Arrays.asList(node));
        ProcessFormFieldDTO amount = field("amount", "number", true, "{\"minimum\":0.01}");
        ProcessFormFieldDTO currency = field("currency", "select", true,
                "{\"options\":[{\"label\":\"人民币\",\"value\":\"CNY\"}]}");
        definition.setFormFields(Arrays.asList(amount, currency));
        return definition;
    }

    private ProcessFormFieldDTO field(String code, String type, boolean required, String rule) {
        ProcessFormFieldDTO field = new ProcessFormFieldDTO(); field.setFieldCode(code);
        field.setFieldType(type); field.setRequired(required); field.setValidationRule(rule); return field;
    }

    private TaskDTO task() { TaskDTO task = new TaskDTO(); task.setNodeCode("apply"); return task; }
}
