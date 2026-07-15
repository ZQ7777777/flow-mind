package com.flowmind.platform.persistence.entity;

import lombok.Data;

/**
 * 流程节点表 process_node 的持久化实体。
 *
 * @author Yuxin Xu
 * @since 2026-07-15
 **/
@Data
public class ProcessNodeEntity {
    /**
     * 节点主键。
     */
    private String id;
    /**
     * 所属流程定义 ID。
     */
    private String definitionId;
    /**
     * 定义内唯一的节点编码。
     */
    private String nodeCode;
    /**
     * 节点名称。
     */
    private String nodeName;
    /**
     * 节点类型，典型值：START、USER_TASK、EXCLUSIVE_GATEWAY、PARALLEL_SPLIT_GATEWAY、PARALLEL_JOIN_GATEWAY、END。
     */
    private String nodeType;
    /**
     * 配对并行网关的节点编码，非并行网关为空。
     */
    private String pairedGatewayCode;
    /**
     * 审批人规则类型，典型值：USER、STARTER、DEPARTMENT、ROLE、ROLE_IN_DEPARTMENT、APPROVER_EXPRESSION。
     */
    private String approverRuleType;
    /**
     * 审批人规则 JSON，包含用户、部门、角色或表达式参数。
     */
    private String approverRuleConfig;
    /**
     * 多人审批模式，典型值：SINGLE、OR_SIGN、COUNTERSIGN。
     */
    private String multiInstanceMode;
    /**
     * 节点监听器 JSON，包含事件类型和监听器配置。
     */
    private String listenerConfig;
    /**
     * 超时 JSON，包含时长、提醒和告警策略。
     */
    private String timeoutConfig;
    /**
     * 催办 JSON，包含提醒频率和目标人规则。
     */
    private String reminderConfig;
    /**
     * 流程图横坐标。
     */
    private Double positionX;
    /**
     * 流程图纵坐标。
     */
    private Double positionY;
    /**
     * 节点展示顺序。
     */
    private Integer sortOrder;
}
