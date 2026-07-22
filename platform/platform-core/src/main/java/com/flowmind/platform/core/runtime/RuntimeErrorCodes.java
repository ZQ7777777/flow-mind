package com.flowmind.platform.core.runtime;

/**
 * M2 运行时配置协作错误码。
 *
 * @author Yuxin Xu
 * @since 2026-07-22
 */
public final class RuntimeErrorCodes {

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
