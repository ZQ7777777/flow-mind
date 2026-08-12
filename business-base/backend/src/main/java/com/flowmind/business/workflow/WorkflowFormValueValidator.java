package com.flowmind.business.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 校验 STARTER/APPLY 返工提交的定义驱动表单值，并阻止写入未声明变量。
 */
@Component
public class WorkflowFormValueValidator {
    private final ObjectMapper objectMapper;

    public WorkflowFormValueValidator(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public Map<String, Object> validate(ProcessDefinitionDetailDTO definition, TaskDTO task,
                                        Map<String, Object> variables) {
        if (definition == null || task == null || variables == null) {
            throw new IllegalArgumentException("返工表单变量不能为空");
        }
        ProcessNodeDTO node = findNode(definition, task.getNodeCode());
        if (node == null || !ApproverRuleTypeEnum.STARTER.equals(node.getApproverRuleType())) {
            throw new IllegalArgumentException("仅申请返工节点允许修改表单字段");
        }
        List<ProcessFormFieldDTO> fields = definition.getFormFields() == null
                ? Collections.<ProcessFormFieldDTO>emptyList() : definition.getFormFields();
        Set<String> allowedCodes = new LinkedHashSet<String>();
        for (ProcessFormFieldDTO field : fields) if (field != null) allowedCodes.add(field.getFieldCode());
        for (String key : variables.keySet()) {
            if (!allowedCodes.contains(key)) throw new IllegalArgumentException("未声明的表单字段: " + key);
        }
        Map<String, Object> validated = new LinkedHashMap<String, Object>();
        for (ProcessFormFieldDTO field : fields) {
            if (field == null) continue;
            Object value = variables.get(field.getFieldCode());
            validateField(field, value);
            if (variables.containsKey(field.getFieldCode())) validated.put(field.getFieldCode(), value);
        }
        return validated;
    }

    private void validateField(ProcessFormFieldDTO field, Object value) {
        String code = field.getFieldCode();
        if (Boolean.TRUE.equals(field.getRequired()) && (value == null
                || value instanceof String && ((String) value).trim().isEmpty())) {
            throw new IllegalArgumentException("必填字段不能为空: " + code);
        }
        if (value == null) return;
        String type = normalize(field.getFieldType());
        if (("string".equals(type) || "select".equals(type)) && !(value instanceof String)) {
            throw new IllegalArgumentException("字段类型不匹配: " + code);
        }
        if ("number".equals(type) && !(value instanceof Number)) {
            throw new IllegalArgumentException("字段类型不匹配: " + code);
        }
        if ("boolean".equals(type) && !(value instanceof Boolean)) {
            throw new IllegalArgumentException("字段类型不匹配: " + code);
        }
        if ("date".equals(type)) {
            if (!(value instanceof String)) throw new IllegalArgumentException("字段类型不匹配: " + code);
            try { LocalDate.parse((String) value); }
            catch (RuntimeException exception) { throw new IllegalArgumentException("日期格式不合法: " + code); }
        }
        validateRule(field, value);
    }

    private void validateRule(ProcessFormFieldDTO field, Object value) {
        if (!hasText(field.getValidationRule())) return;
        JsonNode rule;
        try { rule = objectMapper.readTree(field.getValidationRule()); }
        catch (Exception exception) { throw new IllegalArgumentException("字段校验规则无效: " + field.getFieldCode()); }
        if (value instanceof Number) {
            BigDecimal number = new BigDecimal(String.valueOf(value));
            if (rule.has("minimum") && number.compareTo(rule.get("minimum").decimalValue()) < 0)
                throw invalid(field);
            if (rule.has("maximum") && number.compareTo(rule.get("maximum").decimalValue()) > 0)
                throw invalid(field);
        }
        if (value instanceof String) {
            String text = (String) value;
            if (rule.has("minLength") && text.length() < rule.get("minLength").asInt()) throw invalid(field);
            if (rule.has("maxLength") && text.length() > rule.get("maxLength").asInt()) throw invalid(field);
            if (rule.has("pattern")) {
                try {
                    if (!Pattern.matches(rule.get("pattern").asText(), text)) throw invalid(field);
                } catch (java.util.regex.PatternSyntaxException exception) {
                    throw new IllegalArgumentException("字段校验规则无效: " + field.getFieldCode());
                }
            }
        }
        JsonNode options = rule.path("options");
        if (options.isArray() && options.size() > 0) {
            boolean matched = false;
            for (JsonNode option : options) {
                JsonNode optionValue = option.isObject() ? option.get("value") : option;
                if (optionValue != null && String.valueOf(value).equals(optionValue.asText())) matched = true;
            }
            if (!matched) throw invalid(field);
        }
    }

    private IllegalArgumentException invalid(ProcessFormFieldDTO field) {
        return new IllegalArgumentException("字段值不符合校验规则: " + field.getFieldCode());
    }

    private ProcessNodeDTO findNode(ProcessDefinitionDetailDTO definition, String nodeCode) {
        if (definition.getNodes() == null) return null;
        for (ProcessNodeDTO node : definition.getNodes())
            if (node != null && nodeCode.equals(node.getNodeCode())) return node;
        return null;
    }

    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
