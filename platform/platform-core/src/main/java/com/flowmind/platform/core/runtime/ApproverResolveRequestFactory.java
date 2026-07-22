package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.request.ApproverResolveRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批人解析请求工厂。
 *
 * <p>M2 只负责读取节点审批配置并构造正式 SPI 请求，不对 APPROVER_EXPRESSION 做表达式求值。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-22
 */
@Component
public class ApproverResolveRequestFactory {

    private final RuntimeNodeConfigReader nodeConfigReader;

    /**
     * 使用默认节点配置读取器创建工厂。
     */
    public ApproverResolveRequestFactory() {
        this(new RuntimeNodeConfigReader());
    }

    /**
     * 使用指定节点配置读取器创建工厂。
     *
     * @param nodeConfigReader 节点配置读取器
     */
    public ApproverResolveRequestFactory(RuntimeNodeConfigReader nodeConfigReader) {
        this.nodeConfigReader = nodeConfigReader;
    }

    /**
     * 构造审批人解析请求。
     *
     * @param definition 流程定义详情快照
     * @param instanceId 流程实例 ID
     * @param nodeCode 当前节点编码
     * @param starterUserId 流程发起人用户 ID
     * @param starterDeptId 流程发起人部门 ID
     * @param variables 当前流程变量，JSON 对象语义
     * @return 审批人解析请求
     */
    public ApproverResolveRequest create(ProcessDefinitionDetailDTO definition,
                                         String instanceId,
                                         String nodeCode,
                                         String starterUserId,
                                         String starterDeptId,
                                         Map<String, Object> variables) {
        RuntimeNodeConfig nodeConfig = nodeConfigReader.read(definition, nodeCode);
        if (nodeConfig.getApproverRuleType() == null) {
            throw new RuntimeConfigurationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    "approverRuleType must not be null: " + nodeCode);
        }
        ApproverResolveRequest request = new ApproverResolveRequest();
        request.setDefinitionId(definition == null ? null : definition.getId());
        request.setInstanceId(instanceId);
        request.setNodeCode(nodeConfig.getNode().getNodeCode());
        request.setNodeName(nodeConfig.getNode().getNodeName());
        request.setApproverRuleType(nodeConfig.getApproverRuleType());
        request.setApproverRuleConfig(new LinkedHashMap<String, Object>(nodeConfig.getApproverRuleConfig()));
        request.setStarterUserId(starterUserId);
        request.setStarterDeptId(starterDeptId);
        request.setVariables(variables == null
                ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(variables));
        return request;
    }
}
