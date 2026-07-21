package com.flowmind.platform.core.validation;

/**
 * M0 冻结规则校验错误码。
 *
 * <p>这些错误码用于表达流程定义、运行时状态和模型结构的业务校验失败，
 * 调用方不应把这类失败包装成通用运行时异常。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 */
public final class FrozenValidationErrorCodes {

    public static final String DEFINITION_STATUS_COMBINATION_INVALID =
            "FLOW_FROZEN_DEFINITION_STATUS_COMBINATION_INVALID";
    public static final String DEFINITION_GRAY_RULE_REQUIRED =
            "FLOW_FROZEN_DEFINITION_GRAY_RULE_REQUIRED";
    public static final String INSTANCE_STATUS_TRANSITION_INVALID =
            "FLOW_FROZEN_INSTANCE_STATUS_TRANSITION_INVALID";
    public static final String TASK_STATUS_TRANSITION_INVALID =
            "FLOW_FROZEN_TASK_STATUS_TRANSITION_INVALID";
    public static final String TASK_GROUP_STATUS_TRANSITION_INVALID =
            "FLOW_FROZEN_TASK_GROUP_STATUS_TRANSITION_INVALID";
    public static final String TASK_GROUP_COUNT_INVALID =
            "FLOW_FROZEN_TASK_GROUP_COUNT_INVALID";
    public static final String TASK_GROUP_BRANCH_ARRIVAL_INVALID =
            "FLOW_FROZEN_TASK_GROUP_BRANCH_ARRIVAL_INVALID";
    public static final String OPERATION_STATUS_TRANSITION_INVALID =
            "FLOW_FROZEN_OPERATION_STATUS_TRANSITION_INVALID";
    public static final String MODEL_START_NODE_INVALID =
            "FLOW_FROZEN_MODEL_START_NODE_INVALID";
    public static final String MODEL_END_NODE_REQUIRED =
            "FLOW_FROZEN_MODEL_END_NODE_REQUIRED";
    public static final String MODEL_NODE_REFERENCE_INVALID =
            "FLOW_FROZEN_MODEL_NODE_REFERENCE_INVALID";
    public static final String MODEL_EDGE_REFERENCE_INVALID =
            "FLOW_FROZEN_MODEL_EDGE_REFERENCE_INVALID";
    public static final String MODEL_USER_TASK_APPROVER_REQUIRED =
            "FLOW_FROZEN_MODEL_USER_TASK_APPROVER_REQUIRED";
    public static final String MODEL_GATEWAY_DEFAULT_EDGE_INVALID =
            "FLOW_FROZEN_MODEL_GATEWAY_DEFAULT_EDGE_INVALID";
    public static final String MODEL_PARALLEL_GATEWAY_PAIR_INVALID =
            "FLOW_FROZEN_MODEL_PARALLEL_GATEWAY_PAIR_INVALID";
    public static final String MODEL_ORPHAN_NODE_INVALID =
            "FLOW_FROZEN_MODEL_ORPHAN_NODE_INVALID";
    /** 开始节点或用户任务的出线数量不符合要求。 */
    public static final String MODEL_NODE_OUTGOING_EDGE_INVALID =
            "FLOW_FROZEN_MODEL_NODE_OUTGOING_EDGE_INVALID";
    /** 网关配置了审批人规则或多人办理模式。 */
    public static final String MODEL_GATEWAY_NODE_CONFIGURATION_INVALID =
            "FLOW_FROZEN_MODEL_GATEWAY_NODE_CONFIGURATION_INVALID";
    /** 排他网关的出线数量或条件配置不符合要求。 */
    public static final String MODEL_EXCLUSIVE_GATEWAY_TOPOLOGY_INVALID =
            "FLOW_FROZEN_MODEL_EXCLUSIVE_GATEWAY_TOPOLOGY_INVALID";
    /** 并行网关的出入线、条件或分支拓扑不符合要求。 */
    public static final String MODEL_PARALLEL_GATEWAY_TOPOLOGY_INVALID =
            "FLOW_FROZEN_MODEL_PARALLEL_GATEWAY_TOPOLOGY_INVALID";
    /** 定义图包含无法静态证明有限结束的有向环。 */
    public static final String MODEL_GRAPH_CYCLE_INVALID =
            "FLOW_FROZEN_MODEL_GRAPH_CYCLE_INVALID";
    /** 表单字段配置不符合发布要求。 */
    public static final String MODEL_FORM_FIELD_INVALID =
            "FLOW_FROZEN_MODEL_FORM_FIELD_INVALID";
    /** 附件配置不符合发布要求。 */
    public static final String MODEL_ATTACHMENT_CONFIGURATION_INVALID =
            "FLOW_FROZEN_MODEL_ATTACHMENT_CONFIGURATION_INVALID";
    /** 表单字段必填项缺失。 */
    public static final String FORM_FIELD_REQUIRED =
            "FLOW_FORM_FIELD_REQUIRED";
    /** 表单字段编码重复。 */
    public static final String FORM_FIELD_CODE_DUPLICATED =
            "FLOW_FORM_FIELD_CODE_DUPLICATED";
    /** 表单字段类型非法。 */
    public static final String FORM_FIELD_TYPE_INVALID =
            "FLOW_FORM_FIELD_TYPE_INVALID";
    /** 表单字段控件类型非法。 */
    public static final String FORM_FIELD_CONTROL_TYPE_INVALID =
            "FLOW_FORM_FIELD_CONTROL_TYPE_INVALID";
    /** 表单字段排序值非法。 */
    public static final String FORM_FIELD_SORT_ORDER_INVALID =
            "FLOW_FORM_FIELD_SORT_ORDER_INVALID";
    /** 表单字段校验规则 JSON 非法。 */
    public static final String FORM_FIELD_VALIDATION_RULE_INVALID =
            "FLOW_FORM_FIELD_VALIDATION_RULE_INVALID";
    /** 表单字段默认值非法。 */
    public static final String FORM_FIELD_DEFAULT_VALUE_INVALID =
            "FLOW_FORM_FIELD_DEFAULT_VALUE_INVALID";
    /** 附件模板必填项缺失。 */
    public static final String ATTACHMENT_TEMPLATE_REQUIRED =
            "FLOW_ATTACHMENT_TEMPLATE_REQUIRED";
    /** 附件模板扩展名配置非法。 */
    public static final String ATTACHMENT_TEMPLATE_EXTENSION_INVALID =
            "FLOW_ATTACHMENT_TEMPLATE_EXTENSION_INVALID";
    /** 附件模板大小限制非法。 */
    public static final String ATTACHMENT_TEMPLATE_SIZE_INVALID =
            "FLOW_ATTACHMENT_TEMPLATE_SIZE_INVALID";
    /** 附件模板状态非法。 */
    public static final String ATTACHMENT_TEMPLATE_STATUS_INVALID =
            "FLOW_ATTACHMENT_TEMPLATE_STATUS_INVALID";
    /** 附件模板不存在。 */
    public static final String ATTACHMENT_TEMPLATE_NOT_FOUND =
            "FLOW_ATTACHMENT_TEMPLATE_NOT_FOUND";
    /** 附件模板已被引用。 */
    public static final String ATTACHMENT_TEMPLATE_REFERENCED =
            "FLOW_ATTACHMENT_TEMPLATE_REFERENCED";
    /** 附件配置必填项缺失。 */
    public static final String ATTACHMENT_CONFIG_REQUIRED =
            "FLOW_ATTACHMENT_CONFIG_REQUIRED";
    /** 附件配置引用的模板非法。 */
    public static final String ATTACHMENT_CONFIG_TEMPLATE_INVALID =
            "FLOW_ATTACHMENT_CONFIG_TEMPLATE_INVALID";
    /** 附件配置引用的模板已停用。 */
    public static final String ATTACHMENT_CONFIG_TEMPLATE_DISABLED =
            "FLOW_ATTACHMENT_CONFIG_TEMPLATE_DISABLED";
    /** 附件配置数量约束非法。 */
    public static final String ATTACHMENT_CONFIG_QUANTITY_INVALID =
            "FLOW_ATTACHMENT_CONFIG_QUANTITY_INVALID";
    /** 附件配置适用节点非法。 */
    public static final String ATTACHMENT_CONFIG_NODE_INVALID =
            "FLOW_ATTACHMENT_CONFIG_NODE_INVALID";
    /** 附件配置重复。 */
    public static final String ATTACHMENT_CONFIG_DUPLICATED =
            "FLOW_ATTACHMENT_CONFIG_DUPLICATED";
    /** 附件配置排序值非法。 */
    public static final String ATTACHMENT_CONFIG_SORT_ORDER_INVALID =
            "FLOW_ATTACHMENT_CONFIG_SORT_ORDER_INVALID";

    private FrozenValidationErrorCodes() {
    }
}
