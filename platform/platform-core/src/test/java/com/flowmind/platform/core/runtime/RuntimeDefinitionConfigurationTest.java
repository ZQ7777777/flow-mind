package com.flowmind.platform.core.runtime;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.api.request.ApproverResolveRequest;
import com.flowmind.platform.core.validation.DefinitionGraphIndex;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDefinitionConfigurationTest {

    private final RuntimeNodeConfigReader nodeConfigReader = new RuntimeNodeConfigReader();
    private final ApproverResolveRequestFactory requestFactory = new ApproverResolveRequestFactory();
    private final ConditionExpressionEvaluator expressionEvaluator = new SimpleConditionExpressionEvaluator();

    /**
     * 验证运行时可复用只读流程图索引，并按稳定顺序读取排他网关出线。
     */
    @Test
    void runtimeCanReuseReadOnlyGraphIndexWithStableOutgoingOrder() {
        ProcessDefinitionDetailDTO definition = definition(
                Arrays.asList(userTask("review", 20, ApproverRuleTypeEnum.USER,
                                "{\"userIds\":[\"u1\"]}"),
                        node("start", NodeTypeEnum.START, 10),
                        node("end", NodeTypeEnum.END, 40),
                        node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY, 30)),
                Arrays.asList(edge("to-default", "route", "end", 30, null, true),
                        edge("to-review", "route", "review", 10, "amount > 100000", false),
                        edge("start-route", "start", "route", 5, null, false)));

        DefinitionGraphIndex graph = DefinitionGraphIndex.from(definition);

        assertEquals("start", graph.getStartNodes().get(0).getNodeCode());
        assertEquals("end", graph.getEndNodes().get(0).getNodeCode());
        assertEquals("route", graph.getNode("route").getNodeCode());
        assertEquals(Arrays.asList("to-review", "to-default"), edgeCodes(graph.getOutgoingEdges("route")));
    }

    /**
     * 验证节点配置读取器可以解析审批规则和运行时 JSON 配置。
     */
    @Test
    void nodeConfigReaderParsesApproverAndRuntimeJsonWithoutCallingSpi() {
        ProcessNodeDTO review = userTask("review", 20, ApproverRuleTypeEnum.ROLE_IN_DEPARTMENT,
                "{\"roleCode\":\"manager\",\"departmentField\":\"starterDeptId\"}");
        review.setListenerConfig("{\"onCreate\":\"callback\"}");
        review.setTimeoutConfig("{\"hours\":24}");
        review.setReminderConfig("{\"hours\":12}");

        RuntimeNodeConfig config = nodeConfigReader.read(definition(
                Collections.singletonList(review), Collections.<ProcessEdgeDTO>emptyList()), "review");

        assertEquals("review", config.getNode().getNodeCode());
        assertEquals(ApproverRuleTypeEnum.ROLE_IN_DEPARTMENT, config.getApproverRuleType());
        assertEquals("manager", config.getApproverRuleConfig().get("roleCode"));
        assertEquals("callback", config.getListenerConfig().get("onCreate"));
        assertEquals(Integer.valueOf(24), config.getTimeoutConfig().get("hours"));
        assertEquals(Integer.valueOf(12), config.getReminderConfig().get("hours"));
    }

    /**
     * 验证非法审批规则 JSON 会返回稳定运行时配置错误。
     */
    @Test
    void nodeConfigReaderReturnsStableErrorForInvalidJson() {
        ProcessNodeDTO review = userTask("review", 20, ApproverRuleTypeEnum.USER, "{bad");

        RuntimeConfigurationException exception = assertThrows(RuntimeConfigurationException.class,
                () -> nodeConfigReader.read(definition(Collections.singletonList(review),
                        Collections.<ProcessEdgeDTO>emptyList()), "review"));

        assertEquals(RuntimeErrorCodes.NODE_CONFIG_INVALID, exception.getErrorCode());
    }

    /**
     * 验证 APPROVER_EXPRESSION 只透传表达式内容，不在 A 线求值。
     */
    @Test
    void approverExpressionIsPassedThroughWithoutEvaluation() {
        ProcessNodeDTO review = userTask("review", 20, ApproverRuleTypeEnum.APPROVER_EXPRESSION,
                "{\"expression\":\"departmentManager(starterDeptId)\"}");
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("starterDeptId", "dept-001");

        ApproverResolveRequest request = requestFactory.create(definition(
                Collections.singletonList(review), Collections.<ProcessEdgeDTO>emptyList()),
                "instance-001", "review", "starter-001", "dept-001", variables);

        assertEquals(ApproverRuleTypeEnum.APPROVER_EXPRESSION, request.getApproverRuleType());
        assertEquals("departmentManager(starterDeptId)", request.getApproverRuleConfig().get("expression"));
        assertEquals(variables, request.getVariables());
    }

    /**
     * 验证 APPROVER_EXPRESSION 必须显式声明 expression 字段。
     */
    @Test
    void approverExpressionRequiresExpressionField() {
        ProcessNodeDTO review = userTask("review", 20, ApproverRuleTypeEnum.APPROVER_EXPRESSION,
                "{\"other\":\"value\"}");

        RuntimeConfigurationException exception = assertThrows(RuntimeConfigurationException.class,
                () -> requestFactory.create(definition(Collections.singletonList(review),
                        Collections.<ProcessEdgeDTO>emptyList()), "instance-001",
                        "review", "starter-001", "dept-001", Collections.<String, Object>emptyMap()));

        assertEquals(RuntimeErrorCodes.NODE_CONFIG_INVALID, exception.getErrorCode());
    }

    /**
     * 验证 M2 条件表达式支持数字、字符串和布尔的简单比较。
     */
    @Test
    void conditionExpressionSupportsOnlySimpleComparisons() {
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("amount", new BigDecimal("120000"));
        variables.put("accountType", "company");
        variables.put("urgent", Boolean.TRUE);

        assertTrue(expressionEvaluator.evaluate("amount > 100000", variables));
        assertTrue(expressionEvaluator.evaluate("amount <= 120000", variables));
        assertTrue(expressionEvaluator.evaluate("accountType == \"company\"", variables));
        assertTrue(expressionEvaluator.evaluate("urgent == true", variables));
        assertFalse(expressionEvaluator.evaluate("accountType != \"company\"", variables));
    }

    /**
     * 验证变量缺失和复杂表达式语法会被明确拒绝。
     */
    @Test
    void conditionExpressionRejectsMissingVariablesAndComplexSyntax() {
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        variables.put("amount", Integer.valueOf(10));

        assertThrows(RuntimeConfigurationException.class,
                () -> expressionEvaluator.evaluate("missing > 0", variables));
        assertThrows(RuntimeConfigurationException.class,
                () -> expressionEvaluator.evaluate("amount > 0 && urgent == true", variables));
        assertThrows(RuntimeConfigurationException.class,
                () -> expressionEvaluator.evaluate("riskScore(amount) > 80", variables));
    }

    /**
     * 构造流程定义详情测试夹具。
     *
     * @param nodes 节点列表
     * @param edges 连线列表
     * @return 流程定义详情
     */
    private static ProcessDefinitionDetailDTO definition(List<ProcessNodeDTO> nodes,
                                                         List<ProcessEdgeDTO> edges) {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId("definition-001");
        definition.setNodes(nodes);
        definition.setEdges(edges);
        return definition;
    }

    /**
     * 构造用户任务节点测试夹具。
     *
     * @param code 节点编码
     * @param sortOrder 排序值
     * @param ruleType 审批规则类型
     * @param ruleConfig 审批规则 JSON
     * @return 用户任务节点
     */
    private static ProcessNodeDTO userTask(String code,
                                           int sortOrder,
                                           ApproverRuleTypeEnum ruleType,
                                           String ruleConfig) {
        ProcessNodeDTO node = node(code, NodeTypeEnum.USER_TASK, sortOrder);
        node.setNodeName(code + " node");
        node.setApproverRuleType(ruleType);
        node.setApproverRuleConfig(ruleConfig);
        node.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
        return node;
    }

    /**
     * 构造通用节点测试夹具。
     *
     * @param code 节点编码
     * @param nodeType 节点类型
     * @param sortOrder 排序值
     * @return 流程节点
     */
    private static ProcessNodeDTO node(String code, NodeTypeEnum nodeType, int sortOrder) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setDefinitionId("definition-001");
        node.setNodeCode(code);
        node.setNodeName(code + " node");
        node.setNodeType(nodeType);
        node.setSortOrder(Integer.valueOf(sortOrder));
        node.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
        return node;
    }

    /**
     * 构造连线测试夹具。
     *
     * @param code 连线编码
     * @param source 来源节点编码
     * @param target 目标节点编码
     * @param sortOrder 排序值
     * @param condition 条件表达式
     * @param defaultEdge 是否默认出线
     * @return 流程连线
     */
    private static ProcessEdgeDTO edge(String code,
                                       String source,
                                       String target,
                                       int sortOrder,
                                       String condition,
                                       boolean defaultEdge) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setDefinitionId("definition-001");
        edge.setEdgeCode(code);
        edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target);
        edge.setSortOrder(Integer.valueOf(sortOrder));
        edge.setConditionExpression(condition);
        edge.setDefaultEdge(Boolean.valueOf(defaultEdge));
        return edge;
    }

    /**
     * 提取连线编码列表，便于断言排序。
     *
     * @param edges 连线列表
     * @return 连线编码列表
     */
    private static List<String> edgeCodes(List<ProcessEdgeDTO> edges) {
        List<String> codes = new java.util.ArrayList<String>();
        for (ProcessEdgeDTO edge : edges) {
            codes.add(edge.getEdgeCode());
        }
        return codes;
    }
}
