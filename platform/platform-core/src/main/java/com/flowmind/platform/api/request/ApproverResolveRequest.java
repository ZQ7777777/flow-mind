package com.flowmind.platform.api.request;

import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;

import java.util.Map;

/**
 * 审批人解析请求，承载运行时创建用户任务前所需的节点、规则、发起人和变量快照。
 *
 * @author FlowMind
 * @since 2026-07-24
 */
public class ApproverResolveRequest {

    /** 流程定义 ID，用于审计和排查解析来源。 */
    private String definitionId;
    /** 流程实例 ID，用于审计和排查解析来源。 */
    private String instanceId;
    /** 当前需要解析审批人的节点编码，必须对应一个用户任务节点。 */
    private String nodeCode;
    /** 当前需要解析审批人的节点名称，用于错误提示和审计展示。 */
    private String nodeName;
    /** 审批人规则类型，典型值：USER、STARTER、DEPARTMENT、ROLE、ROLE_IN_DEPARTMENT、APPROVER_EXPRESSION。 */
    private ApproverRuleTypeEnum approverRuleType;
    /**
     * 审批人规则配置，JSON 对象语义。
     *
     * <p>典型格式：USER 使用 userIds 字符串数组；DEPARTMENT 使用 departmentId；
     * ROLE 使用 roleCode；ROLE_IN_DEPARTMENT 使用 roleCode 和 departmentId，departmentId
     * 缺省时可使用 starterDeptId；APPROVER_EXPRESSION 使用 expression，并且只能读取解析器允许的上下文。</p>
     */
    private Map<String, Object> approverRuleConfig;
    /**
     * 多人审批模式，典型值：SINGLE、OR_SIGN、COUNTERSIGN；由节点配置固化后透传给审批人解析和运行时任务创建。
     */
    private MultiInstanceModeEnum multiInstanceMode;
    /** 流程发起人用户 ID，STARTER 规则必须使用该字段。 */
    private String starterUserId;
    /** 流程发起人部门 ID，ROLE_IN_DEPARTMENT 未显式配置 departmentId 时作为默认部门。 */
    private String starterDeptId;
    /** 当前流程变量，JSON 对象语义；APPROVER_EXPRESSION 只能按白名单读取其中字段。 */
    private Map<String, Object> variables;

    public ApproverResolveRequest() {
    }

    public ApproverResolveRequest(String definitionId, String instanceId, String nodeCode, String nodeName,
                                  ApproverRuleTypeEnum approverRuleType, Map<String, Object> approverRuleConfig, String starterUserId,
                                  String starterDeptId, Map<String, Object> variables) {
        this(definitionId, instanceId, nodeCode, nodeName, approverRuleType, approverRuleConfig,
                null, starterUserId, starterDeptId, variables);
    }

    public ApproverResolveRequest(String definitionId, String instanceId, String nodeCode, String nodeName,
                                  ApproverRuleTypeEnum approverRuleType, Map<String, Object> approverRuleConfig,
                                  MultiInstanceModeEnum multiInstanceMode, String starterUserId,
                                  String starterDeptId, Map<String, Object> variables) {
        this.definitionId = definitionId;
        this.instanceId = instanceId;
        this.nodeCode = nodeCode;
        this.nodeName = nodeName;
        this.approverRuleType = approverRuleType;
        this.approverRuleConfig = approverRuleConfig;
        this.multiInstanceMode = multiInstanceMode;
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
