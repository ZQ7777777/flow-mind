package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 流程节点返回对象，描述节点类型、审批人规则、监听配置和画布位置。
 *
 * @author Yuxin Xu
 * @since 2026-07-14
 */
@Data
public class ProcessNodeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 节点主键。
     */
    private String id;
    /**
     * 所属流程定义 ID。
     */
    private String definitionId;
    /**
     * 节点编码，同一定义内唯一。
     */
    private String nodeCode;
    /**
     * 节点名称。
     */
    private String nodeName;
    /**
     * 节点类型，如 START、USER_TASK、EXCLUSIVE_GATEWAY、END。
     */
    private NodeTypeEnum nodeType;
    /**
     * 配对网关节点编码，仅并行分支/汇聚网关使用。
     */
    private String pairedGatewayCode;
    /**
     * 审批人规则类型，如 USER、STARTER、ROLE。
     */
    private ApproverRuleTypeEnum approverRuleType;
    /**
     * 审批人规则 JSON 配置。
     */
    private String approverRuleConfig;
    /**
     * 多人审批模式，如 SINGLE、OR_SIGN、COUNTERSIGN。
     */
    private MultiInstanceModeEnum multiInstanceMode;
    /**
     * 节点监听器 JSON 配置。
     */
    private String listenerConfig;
    /**
     * 超时策略 JSON 配置。
     */
    private String timeoutConfig;
    /**
     * 催办策略 JSON 配置。
     */
    private String reminderConfig;
    /**
     * 设计器画布 X 坐标。
     */
    private Double positionX;
    /**
     * 设计器画布 Y 坐标。
     */
    private Double positionY;
    /**
     * 节点展示或处理顺序。
     */
    private Integer sortOrder;
}
