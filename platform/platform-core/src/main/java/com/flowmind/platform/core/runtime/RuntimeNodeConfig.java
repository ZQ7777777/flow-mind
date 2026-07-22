package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import lombok.Data;

import java.util.Map;

/**
 * 运行时读取到的节点配置快照。
 *
 * @author Yuxin Xu
 * @since 2026-07-22
 */
@Data
public class RuntimeNodeConfig {

    /**
     * 原始节点 DTO，只读使用，不应在运行时修改。
     */
    private ProcessNodeDTO node;

    /**
     * 节点类型，典型取值：START、USER_TASK、EXCLUSIVE_GATEWAY、PARALLEL_SPLIT_GATEWAY、PARALLEL_JOIN_GATEWAY、END。
     */
    private NodeTypeEnum nodeType;

    /**
     * 审批人规则类型，典型取值：USER、STARTER、DEPARTMENT、ROLE、ROLE_IN_DEPARTMENT、APPROVER_EXPRESSION。
     */
    private ApproverRuleTypeEnum approverRuleType;

    /**
     * 审批人规则配置，来源于 approverRuleConfig JSON 对象。
     */
    private Map<String, Object> approverRuleConfig;

    /**
     * 多人审批模式，M2 仅支持 SINGLE，OR_SIGN 和 COUNTERSIGN 留到后续阶段。
     */
    private MultiInstanceModeEnum multiInstanceMode;

    /**
     * 配对网关节点编码，仅并行拆分和汇聚网关使用。
     */
    private String pairedGatewayCode;

    /**
     * 节点监听配置，来源于 listenerConfig JSON 对象。
     */
    private Map<String, Object> listenerConfig;

    /**
     * 超时配置，来源于 timeoutConfig JSON 对象。
     */
    private Map<String, Object> timeoutConfig;

    /**
     * 提醒配置，来源于 reminderConfig JSON 对象。
     */
    private Map<String, Object> reminderConfig;
}
