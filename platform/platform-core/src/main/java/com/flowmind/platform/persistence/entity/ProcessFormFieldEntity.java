package com.flowmind.platform.persistence.entity;

import lombok.Data;

/** 流程表单字段表 process_form_field 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessFormFieldEntity {
    /**
     * 表单字段主键。
     */
    private String id;
    /**
     * 所属流程定义 ID。
     */
    private String definitionId;
    /**
     * 定义内唯一的字段编码。
     */
    private String fieldCode;
    /**
     * 字段名称。
     */
    private String fieldName;
    /**
     * 数据类型，典型值：string、number、date、boolean、select。
     */
    private String fieldType;
    /**
     * 控件类型，典型值：input、textarea、number、datePicker、select。
     */
    private String controlType;
    /**
     * 是否必填。
     */
    private Boolean required;
    /**
     * 字段校验 JSON，包含格式、范围或长度规则。
     */
    private String validationRule;
    /**
     * 默认值的文本表示。
     */
    private String defaultValue;
    /**
     * 字段展示顺序。
     */
    private Integer sortOrder;
}
