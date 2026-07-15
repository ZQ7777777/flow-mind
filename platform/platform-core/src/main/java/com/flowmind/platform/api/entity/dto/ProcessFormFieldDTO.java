package com.flowmind.platform.api.entity.dto;

import java.util.Map;

/**
 * 流程表单字段定义。
 */
public class ProcessFormFieldDTO {

    /** 表单字段 ID。 */
    private String fieldId;
    /** 流程定义 ID。 */
    private String definitionId;
    /** 字段编码。 */
    private String fieldCode;
    /** 字段名称。 */
    private String fieldName;
    /** 字段类型。 */
    private String fieldType;
    /** 前端控件类型。 */
    private String controlType;
    /** 是否必填。 */
    private Boolean required;
    /** 校验规则配置。 */
    private Map<String, Object> validationRule;
    /** 默认值。 */
    private Object defaultValue;
    /** 展示顺序。 */
    private Integer sortOrder;

    public ProcessFormFieldDTO() {
    }

    public String getFieldId() {
        return fieldId;
    }

    public void setFieldId(String fieldId) {
        this.fieldId = fieldId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getControlType() {
        return controlType;
    }

    public void setControlType(String controlType) {
        this.controlType = controlType;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Map<String, Object> getValidationRule() {
        return validationRule;
    }

    public void setValidationRule(Map<String, Object> validationRule) {
        this.validationRule = validationRule;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
