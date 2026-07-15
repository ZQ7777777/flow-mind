package com.flowmind.platform.api.entity.dto;

import com.flowmind.platform.api.entity.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.entity.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.entity.enums.NodeTypeEnum;

import java.util.Map;

/**
 * 流程节点定义。
 */
public class ProcessNodeDTO {

    /** 节点 ID。 */
    private String nodeId;
    /** 流程定义 ID。 */
    private String definitionId;
    /** 节点编码。 */
    private String nodeCode;
    /** 节点名称。 */
    private String nodeName;
    /** 节点类型。 */
    private NodeTypeEnum nodeType;
    /** 配对网关节点编码，仅并行分支或汇聚网关使用。 */
    private String pairedGatewayCode;
    /** 审批人规则类型。 */
    private ApproverRuleTypeEnum approverRuleType;
    /** 审批人规则配置。 */
    private Map<String, Object> approverRuleConfig;
    /** 多人处理模式。 */
    private MultiInstanceModeEnum multiInstanceMode;
    /** 节点监听配置。 */
    private Map<String, Object> listenerConfig;
    /** 超时配置。 */
    private Map<String, Object> timeoutConfig;
    /** 催办配置。 */
    private Map<String, Object> reminderConfig;
    /** 流程图画布 X 坐标。 */
    private Integer positionX;
    /** 流程图画布 Y 坐标。 */
    private Integer positionY;
    /** 展示或执行顺序。 */
    private Integer sortOrder;

    public ProcessNodeDTO() {
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getNodeCode() {
        return nodeCode;
    }

    public void setNodeCode(String nodeCode) {
        this.nodeCode = nodeCode;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public NodeTypeEnum getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeTypeEnum nodeType) {
        this.nodeType = nodeType;
    }

    public String getPairedGatewayCode() {
        return pairedGatewayCode;
    }

    public void setPairedGatewayCode(String pairedGatewayCode) {
        this.pairedGatewayCode = pairedGatewayCode;
    }

    public ApproverRuleTypeEnum getApproverRuleType() {
        return approverRuleType;
    }

    public void setApproverRuleType(ApproverRuleTypeEnum approverRuleType) {
        this.approverRuleType = approverRuleType;
    }

    public Map<String, Object> getApproverRuleConfig() {
        return approverRuleConfig;
    }

    public void setApproverRuleConfig(Map<String, Object> approverRuleConfig) {
        this.approverRuleConfig = approverRuleConfig;
    }

    public MultiInstanceModeEnum getMultiInstanceMode() {
        return multiInstanceMode;
    }

    public void setMultiInstanceMode(MultiInstanceModeEnum multiInstanceMode) {
        this.multiInstanceMode = multiInstanceMode;
    }

    public Map<String, Object> getListenerConfig() {
        return listenerConfig;
    }

    public void setListenerConfig(Map<String, Object> listenerConfig) {
        this.listenerConfig = listenerConfig;
    }

    public Map<String, Object> getTimeoutConfig() {
        return timeoutConfig;
    }

    public void setTimeoutConfig(Map<String, Object> timeoutConfig) {
        this.timeoutConfig = timeoutConfig;
    }

    public Map<String, Object> getReminderConfig() {
        return reminderConfig;
    }

    public void setReminderConfig(Map<String, Object> reminderConfig) {
        this.reminderConfig = reminderConfig;
    }

    public Integer getPositionX() {
        return positionX;
    }

    public void setPositionX(Integer positionX) {
        this.positionX = positionX;
    }

    public Integer getPositionY() {
        return positionY;
    }

    public void setPositionY(Integer positionY) {
        this.positionY = positionY;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
