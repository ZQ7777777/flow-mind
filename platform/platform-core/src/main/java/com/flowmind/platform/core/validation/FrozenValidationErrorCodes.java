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

    private FrozenValidationErrorCodes() {
    }
}
