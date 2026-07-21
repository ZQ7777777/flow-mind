package com.flowmind.platform.core.definition;

/**
 * 流程定义管理 M1 错误码常量。
 *
 * @author Yuxin Xu
 * @since 2026-07-20
 */
public final class DefinitionErrorCodes {

    /**
     * 流程定义不存在。
     */
    public static final String DEFINITION_NOT_FOUND = "FLOW_DEFINITION_NOT_FOUND";
    /**
     * 流程定义请求或结构非法。
     */
    public static final String DEFINITION_INVALID = "FLOW_DEFINITION_INVALID";
    /**
     * 连线引用了不存在的节点。
     */
    public static final String NODE_NOT_FOUND = "FLOW_NODE_NOT_FOUND";
    /**
     * 幂等操作号缺失。
     */
    public static final String OPERATION_ID_REQUIRED = "FLOW_OPERATION_ID_REQUIRED";
    /**
     * 同一幂等操作号对应了不同请求或不同动作。
     */
    public static final String OPERATION_ID_CONFLICT = "FLOW_OPERATION_ID_CONFLICT";
    /**
     * 同一幂等操作仍在有效租约内处理中。
     */
    public static final String OPERATION_IN_PROGRESS = "FLOW_OPERATION_IN_PROGRESS";
    /**
     * 流程定义当前状态不可编辑或不可删除。
     */
    public static final String DEFINITION_NOT_EDITABLE = "FLOW_DEFINITION_NOT_EDITABLE";
    /**
     * 流程定义版本分配冲突。
     */
    public static final String DEFINITION_VERSION_CONFLICT = "FLOW_DEFINITION_VERSION_CONFLICT";
    /**
     * 流程定义已被其他编辑请求更新。
     */
    public static final String DEFINITION_CONCURRENT_MODIFIED = "FLOW_DEFINITION_CONCURRENT_MODIFIED";
    /**
     * 流程定义已被运行数据使用。
     */
    public static final String DEFINITION_IN_USE = "FLOW_DEFINITION_IN_USE";
    /**
     * 同一定义内节点编码重复。
     */
    public static final String NODE_CODE_DUPLICATED = "FLOW_NODE_CODE_DUPLICATED";
    /**
     * 同一定义内连线编码重复。
     */
    public static final String EDGE_CODE_DUPLICATED = "FLOW_EDGE_CODE_DUPLICATED";

    private DefinitionErrorCodes() {
    }
}
