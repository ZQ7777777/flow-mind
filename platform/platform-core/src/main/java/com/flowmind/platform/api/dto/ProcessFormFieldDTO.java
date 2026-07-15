package com.flowmind.platform.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 流程表单字段返回对象，描述流程变量对应的表单控件和校验规则。
 *
 * @author Yuxin Xu
 * @since 2026-07-14
 */
@Data
public class ProcessFormFieldDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 表单字段主键。
     */
    private String id;
    /**
     * 所属流程定义 ID。
     */
    private String definitionId;
    /**
     * 字段编码，同一定义内唯一。
     */
    private String fieldCode;
    /**
     * 字段名称。
     */
    private String fieldName;
    /**
     * 字段数据类型，如 string、number、date、boolean、select。
     */
    private String fieldType;
    /**
     * 前端控件类型，如 input、textarea、number、datePicker、select。
     */
    private String controlType;
    /**
     * 是否必填。
     */
    private Boolean required;
    /**
     * 校验规则 JSON 配置。
     */
    private String validationRule;
    /**
     * 默认值。
     */
    private String defaultValue;
    /**
     * 表单展示顺序。
     */
    private Integer sortOrder;

}
