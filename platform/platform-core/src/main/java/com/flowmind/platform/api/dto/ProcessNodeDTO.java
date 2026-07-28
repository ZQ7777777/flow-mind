package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 流程节点数据传输对象，描述节点类型、审批人规则、监听配置和设计器位置信息。
 *
 * @author Yuxin Xu
 * @since 2026-07-14
 */
@Data
public class ProcessNodeDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 节点主键，持久化后由平台生成，新增草稿节点可为空。
     */
    private String id;
    /**
     * 所属流程定义 ID，保存流程图时由平台绑定。
     */
    private String definitionId;
    /**
     * 节点编码，同一流程定义内唯一，供连线、配置引用和运行时定位使用。
     */
    private String nodeCode;
    /**
     * 节点名称，用于设计器展示和任务展示。
     */
    private String nodeName;
    /**
     * 节点类型，典型取值为 START、USER_TASK、EXCLUSIVE_GATEWAY、PARALLEL_SPLIT_GATEWAY、PARALLEL_JOIN_GATEWAY、END。
     */
    private NodeTypeEnum nodeType;
    /**
     * 配对网关节点编码，仅并行分支网关和并行汇聚网关使用。
     */
    private String pairedGatewayCode;
    /**
     * 审批人规则类型，典型取值为 USER、STARTER、ROLE。
     */
    private ApproverRuleTypeEnum approverRuleType;
    /**
     * 审批人规则 JSON 配置，内容由 approverRuleType 决定；STARTER 类型可为空。
     */
    private String approverRuleConfig;
    /**
     * 多人审批模式，典型取值为 SINGLE、OR_SIGN、COUNTERSIGN；M5 阶段仅用户任务可配置
     * OR_SIGN/COUNTERSIGN，非用户任务的 SINGLE 视为无多人审批配置。
     */
    private MultiInstanceModeEnum multiInstanceMode;
    /**
     * 节点监听器 JSON 配置，必须为 JSON 对象；M5 增强动作规则继续承载在 taskActionRules 下，
     * 典型字段为 reject.enabled、reject.targetNodeCodes、directSend.enabled、directSend.targetMode=REJECT_SOURCE。
     */
    private String listenerConfig;
    /**
     * 超时策略 JSON 配置，记录超时时长、处理策略等节点运行时参数。
     */
    private String timeoutConfig;
    /**
     * 催办策略 JSON 配置，记录催办是否启用、间隔和次数等节点运行时参数。
     */
    private String reminderConfig;
    /**
     * 设计器画布 X 坐标，单位为设计器坐标系像素。
     */
    private Double positionX;
    /**
     * 设计器画布 Y 坐标，单位为设计器坐标系像素。
     */
    private Double positionY;
    /**
     * 节点展示或处理顺序，数值越小越靠前。
     */
    private Integer sortOrder;
}
