package com.flowmind.business.workflow.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用业务发起页渲染上下文。
 *
 * @author FlowMind
 * @since 2026-08-18
 */
@Data
public class WorkflowStartContextResponse {
    /** 当前活动流程定义 ID；不可发起时可为空。 */
    private String definitionId;
    /** 流程编码。 */
    private String processCode;
    /** 流程名称。 */
    private String processName;
    /** 当前活动定义版本；不可发起时可为空。 */
    private Integer definitionVersion;
    /** 发起后由申请人办理并立即提交的节点编码。 */
    private String currentNodeCode;
    /** 当前用户是否可以发起。 */
    private Boolean startable;
    /** 不可发起原因；可发起时为空。 */
    private String disabledReason;
    /** 当前活动定义的业务字段。 */
    private List<FormFieldView> formFields = new ArrayList<FormFieldView>();
    /** 申请节点适用的附件规则。 */
    private List<AttachmentRuleView> attachments = new ArrayList<AttachmentRuleView>();

    /** 表单字段及首期兼容节点权限。 */
    @Data
    public static class FormFieldView {
        /** 字段编码。 */
        private String fieldCode;
        /** 字段名称。 */
        private String fieldName;
        /** 数据类型，如 string、number、date、boolean、select。 */
        private String fieldType;
        /** 前端控件类型。 */
        private String controlType;
        /** 默认值文本。 */
        private String defaultValue;
        /** JSON 校验规则。 */
        private String validationRule;
        /** 从校验规则解析的下拉选项。 */
        private List<OptionView> options = new ArrayList<OptionView>();
        /** 首期兼容权限，恒为 true。 */
        private Boolean visible;
        /** 首期兼容权限，恒为 true。 */
        private Boolean editable;
        /** 首期沿用字段定义 required。 */
        private Boolean required;
        /** 展示顺序。 */
        private Integer sortOrder;
    }

    /** 表单选择项。 */
    @Data
    public static class OptionView {
        /** 选项展示文本。 */
        private String label;
        /** 选项提交值。 */
        private String value;
    }

    /** 申请节点附件规则。 */
    @Data
    public static class AttachmentRuleView {
        /** 附件编码，也是 multipart 文件部分名称。 */
        private String attachmentCode;
        /** 附件名称。 */
        private String attachmentName;
        /** 附件说明。 */
        private String description;
        /** 是否必填。 */
        private Boolean required;
        /** 最小文件数量。 */
        private Integer minCount;
        /** 最大文件数量。 */
        private Integer maxCount;
        /** 单个文件最大字节数。 */
        private Long maxSizeBytes;
        /** 允许的文件扩展名。 */
        private List<String> allowedExtensions = new ArrayList<String>();
        /** 展示顺序。 */
        private Integer sortOrder;
    }
}
