package com.flowmind.platform.api.request;

import com.flowmind.platform.api.enums.ApproverRuleType;

import java.util.Map;

public class ApproverResolveRequest {

    /** 流程定义 ID。 */
    private String definitionId;
    /** 流程实例 ID。 */
    private String instanceId;
    /** 当前需要解析审批人的节点编码。 */
    private String nodeCode;
    /** 当前需要解析审批人的节点名称。 */
    private String nodeName;
    /** 审批人规则类型。 */
    private ApproverRuleType approverRuleType;
    /** 审批人规则配置。 */
    private Map<String, Object> approverRuleConfig;
    /** 流程发起人用户 ID。 */
    private String starterUserId;
    /** 流程发起人部门 ID。 */
    private String starterDeptId;
    /** 当前流程变量。 */
    private Map<String, Object> variables;

    public ApproverResolveRequest() {
    }

    public ApproverResolveRequest(String definitionId, String instanceId, String nodeCode, String nodeName,
            ApproverRuleType approverRuleType, Map<String, Object> approverRuleConfig, String starterUserId,
            String starterDeptId, Map<String, Object> variables) {
        this.definitionId = definitionId;
        this.instanceId = instanceId;
        this.nodeCode = nodeCode;
        this.nodeName = nodeName;
        this.approverRuleType = approverRuleType;
        this.approverRuleConfig = approverRuleConfig;
        this.starterUserId = starterUserId;
        this.starterDeptId = starterDeptId;
        this.variables = variables;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
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

    public ApproverRuleType getApproverRuleType() {
        return approverRuleType;
    }

    public void setApproverRuleType(ApproverRuleType approverRuleType) {
        this.approverRuleType = approverRuleType;
    }

    public Map<String, Object> getApproverRuleConfig() {
        return approverRuleConfig;
    }

    public void setApproverRuleConfig(Map<String, Object> approverRuleConfig) {
        this.approverRuleConfig = approverRuleConfig;
    }

    public String getStarterUserId() {
        return starterUserId;
    }

    public void setStarterUserId(String starterUserId) {
        this.starterUserId = starterUserId;
    }

    public String getStarterDeptId() {
        return starterDeptId;
    }

    public void setStarterDeptId(String starterDeptId) {
        this.starterDeptId = starterDeptId;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
}
