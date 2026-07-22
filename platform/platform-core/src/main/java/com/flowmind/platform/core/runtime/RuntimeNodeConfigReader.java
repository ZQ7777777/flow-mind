package com.flowmind.platform.core.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.core.validation.DefinitionGraphIndex;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行时节点配置读取器。
 *
 * <p>该组件只从流程定义快照读取和解析配置，不访问数据库，不调用 SPI，不承担运行时事务职责。</p>
 *
 * @author Yuxin Xu
 * @since 2026-07-22
 */
@Component
public class RuntimeNodeConfigReader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final ObjectMapper objectMapper;

    /**
     * 使用默认 JSON 解析器创建读取器。
     */
    public RuntimeNodeConfigReader() {
        this(new ObjectMapper());
    }

    /**
     * 使用指定 JSON 解析器创建读取器。
     *
     * @param objectMapper JSON 解析器
     */
    public RuntimeNodeConfigReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 读取指定节点的运行时配置。
     *
     * @param definition 流程定义详情快照
     * @param nodeCode 节点编码
     * @return 节点运行时配置
     */
    public RuntimeNodeConfig read(ProcessDefinitionDetailDTO definition, String nodeCode) {
        DefinitionGraphIndex graph = DefinitionGraphIndex.from(definition);
        ProcessNodeDTO node = graph.getNode(nodeCode);
        if (node == null) {
            throw new RuntimeConfigurationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    "runtime node config not found: " + nodeCode);
        }
        validateRequiredNodeConfig(node);
        Map<String, Object> approverRuleConfig =
                parseObject(node.getApproverRuleConfig(), "approverRuleConfig", nodeCode);
        validateApproverRuleConfig(node, approverRuleConfig);
        RuntimeNodeConfig config = new RuntimeNodeConfig();
        config.setNode(node);
        config.setNodeType(node.getNodeType());
        config.setApproverRuleType(node.getApproverRuleType());
        config.setApproverRuleConfig(approverRuleConfig);
        config.setMultiInstanceMode(node.getMultiInstanceMode());
        config.setPairedGatewayCode(node.getPairedGatewayCode());
        config.setListenerConfig(parseObject(node.getListenerConfig(), "listenerConfig", nodeCode));
        config.setTimeoutConfig(parseObject(node.getTimeoutConfig(), "timeoutConfig", nodeCode));
        config.setReminderConfig(parseObject(node.getReminderConfig(), "reminderConfig", nodeCode));
        return config;
    }

    /**
     * 校验节点在运行时读取阶段必须具备的基础配置。
     *
     * @param node 待校验节点
     */
    private void validateRequiredNodeConfig(ProcessNodeDTO node) {
        if (node.getNodeType() == null) {
            throw new RuntimeConfigurationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    "runtime node type must not be null: " + node.getNodeCode());
        }
        if (NodeTypeEnum.USER_TASK.equals(node.getNodeType()) && node.getApproverRuleType() == null) {
            throw new RuntimeConfigurationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    "user task approverRuleType must not be null: " + node.getNodeCode());
        }
    }

    /**
     * 校验用户任务审批规则配置是否满足 M2 运行时读取要求。
     *
     * @param node 用户任务节点
     * @param approverRuleConfig 已解析的审批规则配置
     */
    private void validateApproverRuleConfig(ProcessNodeDTO node, Map<String, Object> approverRuleConfig) {
        if (!NodeTypeEnum.USER_TASK.equals(node.getNodeType())) {
            return;
        }
        ApproverRuleTypeEnum ruleType = node.getApproverRuleType();
        if (ApproverRuleTypeEnum.STARTER.equals(ruleType)) {
            return;
        }
        if (approverRuleConfig.isEmpty()) {
            throw new RuntimeConfigurationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    "user task approverRuleConfig must not be empty: " + node.getNodeCode());
        }
        if (ApproverRuleTypeEnum.APPROVER_EXPRESSION.equals(ruleType)) {
            Object expression = approverRuleConfig.get("expression");
            if (!(expression instanceof String) || isBlank((String) expression)) {
                throw new RuntimeConfigurationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                        "APPROVER_EXPRESSION must define expression: " + node.getNodeCode());
            }
        }
    }

    /**
     * 将节点 JSON 配置解析为只读 Map。
     *
     * @param json JSON 对象字符串；空值按空配置处理
     * @param fieldName 配置字段名称，用于错误信息
     * @param nodeCode 节点编码，用于错误信息
     * @return 只读配置 Map
     */
    private Map<String, Object> parseObject(String json, String fieldName, String nodeCode) {
        if (isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, MAP_TYPE);
            return parsed == null ? Collections.<String, Object>emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(parsed));
        } catch (IOException ex) {
            throw new RuntimeConfigurationException(RuntimeErrorCodes.NODE_CONFIG_INVALID,
                    "node " + nodeCode + " " + fieldName + " must be valid JSON object.", ex);
        }
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待判断字符串
     * @return 字符串为 null 或去空格后为空时返回 true
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
