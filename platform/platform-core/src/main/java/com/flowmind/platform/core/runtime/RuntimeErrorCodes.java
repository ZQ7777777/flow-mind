package com.flowmind.platform.core.runtime;

/**
 * M2 运行时使用的冻结错误码。
 *
 * @author FlowMind
 * @since 2026-07-22
 */
public final class RuntimeErrorCodes {

    /** 流程定义不存在。 */
    public static final String DEFINITION_NOT_FOUND = "FLOW_DEFINITION_NOT_FOUND";
    /** 流程定义未处于可启动状态。 */
    public static final String DEFINITION_NOT_ACTIVE = "FLOW_DEFINITION_NOT_ACTIVE";
    /** 流程定义内容或实例快照不一致。 */
    public static final String DEFINITION_INVALID = "FLOW_DEFINITION_INVALID";
    /** 目标流程节点不存在。 */
    public static final String NODE_NOT_FOUND = "FLOW_NODE_NOT_FOUND";
    /** 请求参数或当前状态不支持该动作。 */
    public static final String INVALID_ACTION = "FLOW_INVALID_ACTION";
    /** 幂等操作号缺失。 */
    public static final String OPERATION_ID_REQUIRED = "FLOW_OPERATION_ID_REQUIRED";
    /** 同一幂等操作号对应了不同请求。 */
    public static final String OPERATION_ID_CONFLICT = "FLOW_OPERATION_ID_CONFLICT";
    /** 同一幂等操作仍在有效租约内执行。 */
    public static final String OPERATION_IN_PROGRESS = "FLOW_OPERATION_IN_PROGRESS";
    /** 审批人解析失败或结果为空。 */
    public static final String APPROVER_RESOLVE_FAILED = "FLOW_APPROVER_RESOLVE_FAILED";
    /** 活动任务不存在。 */
    public static final String TASK_NOT_FOUND = "FLOW_TASK_NOT_FOUND";
    /** 活动任务不处于可办理状态。 */
    public static final String TASK_NOT_ACTIVE = "FLOW_TASK_NOT_ACTIVE";
    /** 当前用户没有任务办理权限。 */
    public static final String TASK_PERMISSION_DENIED = "FLOW_TASK_PERMISSION_DENIED";
    /** 任务乐观锁版本不匹配。 */
    public static final String TASK_CONCURRENT_MODIFIED = "FLOW_TASK_CONCURRENT_MODIFIED";
    /** 任务组乐观锁版本不匹配且有限重试耗尽。 */
    public static final String TASK_GROUP_CONCURRENT_MODIFIED = "FLOW_TASK_GROUP_CONCURRENT_MODIFIED";
    /** 条件网关没有命中出线且未配置默认出线。 */
    public static final String GATEWAY_NO_MATCH = "FLOW_GATEWAY_NO_MATCH";
    /** 网关连线、配对关系或自动路径配置无效。 */
    public static final String GATEWAY_CONFIG_INVALID = "FLOW_GATEWAY_CONFIG_INVALID";
    /** 并行上下文引用的任务组不存在。 */
    public static final String PARALLEL_GROUP_NOT_FOUND = "FLOW_PARALLEL_GROUP_NOT_FOUND";
    /** 并行分支重复到达或与任务组状态不一致。 */
    public static final String PARALLEL_JOIN_CONFLICT = "FLOW_PARALLEL_JOIN_CONFLICT";
    /**
     * 节点运行配置非法，典型场景：节点不存在、审批规则缺失、JSON 配置无法解析。
     */
    public static final String NODE_CONFIG_INVALID = "FLOW_RUNTIME_NODE_CONFIG_INVALID";

    /**
     * 条件表达式非法或不在 M2 支持范围内。
     */
    public static final String CONDITION_EXPRESSION_INVALID = "FLOW_RUNTIME_CONDITION_EXPRESSION_INVALID";

    /**
     * 工具类禁止实例化。
     */
    private RuntimeErrorCodes() {
    }
}
