package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionModelValidatorM5Test {

    private final DefinitionModelValidator validator = new DefinitionModelValidator();

    @Test
    void allowsDefaultSingleModeOnGatewayAfterDraftNormalization() {
        ProcessNodeDTO gateway = node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY);
        gateway.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), gateway, userTask("approve"), userTask("reject"), end("end")),
                edges(edge("e1", "start", "route"),
                        conditionalEdge("e2", "route", "approve", "amount >= 1000"),
                        defaultEdge("e3", "route", "reject"),
                        edge("e4", "approve", "end"),
                        edge("e5", "reject", "end")));

        ValidationResult result = validator.validate(definition);

        assertTrue(result.isValid());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void rejectsGroupedMultiInstanceModeOnNonUserTask() {
        ProcessNodeDTO start = start("start");
        start.setMultiInstanceMode(MultiInstanceModeEnum.OR_SIGN);
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start, userTask("review"), end("end")),
                edges(edge("e1", "start", "review"), edge("e2", "review", "end")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_MULTI_INSTANCE_CONFIGURATION_INVALID,
                "start", null);
    }

    @Test
    void rejectsGroupedMultiInstanceModeWithoutResolvableApproverConfig() {
        ProcessNodeDTO review = userTask("review");
        review.setMultiInstanceMode(MultiInstanceModeEnum.COUNTERSIGN);
        review.setApproverRuleConfig("{\"userIds\":[]}");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), review, end("end")),
                edges(edge("e1", "start", "review"), edge("e2", "review", "end")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_MULTI_INSTANCE_CONFIGURATION_INVALID,
                "review", null);
    }

    @Test
    void rejectsConditionExpressionOutsideExclusiveGateway() {
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), userTask("review"), end("end")),
                edges(conditionalEdge("e1", "start", "review", "amount > 0"),
                        edge("e2", "review", "end")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_CONDITION_EXPRESSION_INVALID,
                "start", "e1");
    }

    @Test
    void rejectsUnsupportedExclusiveGatewayConditionSyntax() {
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY),
                        userTask("approve"), userTask("reject"), end("end")),
                edges(edge("e1", "start", "route"),
                        conditionalEdge("e2", "route", "approve", "amount > 0 && urgent == true"),
                        defaultEdge("e3", "route", "reject"),
                        edge("e4", "approve", "end"),
                        edge("e5", "reject", "end")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_CONDITION_EXPRESSION_INVALID,
                "route", "e2");
    }

    @Test
    void acceptsTaskActionRulesFromListenerConfigOnUserTask() {
        ProcessNodeDTO review = userTask("review");
        review.setMultiInstanceMode(MultiInstanceModeEnum.OR_SIGN);
        review.setListenerConfig("{\"listeners\":[{\"eventType\":\"TASK_CREATED\","
                + "\"handlerId\":\"workflowCallbackHandler\",\"failureStrategy\":\"IGNORE\","
                + "\"params\":{\"channel\":\"mock\"}}],"
                + "\"taskActionRules\":{\"reject\":{\"enabled\":true,"
                + "\"targetNodeCodes\":[\"draft\"]},\"directSend\":{\"enabled\":true,"
                + "\"targetMode\":\"REJECT_SOURCE\"}}}");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), userTask("draft"), review, end("end")),
                edges(edge("e1", "start", "draft"), edge("e2", "draft", "review"),
                        edge("e3", "review", "end")));

        ValidationResult result = validator.validate(definition);

        assertTrue(result.isValid());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void rejectsTaskActionRuleTargetThatIsNotUserTask() {
        ProcessNodeDTO review = userTask("review");
        review.setListenerConfig("{\"taskActionRules\":{\"reject\":{\"enabled\":true,"
                + "\"targetNodeCodes\":[\"start\"]}}}");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), review, end("end")),
                edges(edge("e1", "start", "review"), edge("e2", "review", "end")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_TASK_ACTION_RULE_INVALID,
                "review", null);
    }

    @Test
    void rejectsTaskActionRulesOnGatewayNode() {
        ProcessNodeDTO gateway = node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY);
        gateway.setListenerConfig("{\"taskActionRules\":{\"directSend\":{\"enabled\":true,"
                + "\"targetMode\":\"REJECT_SOURCE\"}}}");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), gateway, userTask("approve"), userTask("reject"), end("end")),
                edges(edge("e1", "start", "route"),
                        conditionalEdge("e2", "route", "approve", "amount > 0"),
                        defaultEdge("e3", "route", "reject"),
                        edge("e4", "approve", "end"),
                        edge("e5", "reject", "end")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_TASK_ACTION_RULE_INVALID,
                "route", null);
    }

    @Test
    void rejectsUnsupportedDirectSendTargetMode() {
        ProcessNodeDTO review = userTask("review");
        review.setListenerConfig("{\"taskActionRules\":{\"directSend\":{\"enabled\":true,"
                + "\"targetMode\":\"ANY_NODE\"}}}");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), review, end("end")),
                edges(edge("e1", "start", "review"), edge("e2", "review", "end")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_TASK_ACTION_RULE_INVALID,
                "review", null);
    }

    @Test
    void rejectsListenerConfigWithUnsupportedNodeEvent() {
        ProcessNodeDTO review = userTask("review");
        review.setListenerConfig("{\"listeners\":[{\"eventType\":\"PROCESS_STARTED\","
                + "\"handlerId\":\"workflowCallbackHandler\"}]}");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), review, end("end")),
                edges(edge("e1", "start", "review"), edge("e2", "review", "end")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_TASK_ACTION_RULE_INVALID,
                "review", null);
    }

    @Test
    void rejectsListenerConfigWithUnregisteredHandler() {
        ProcessNodeDTO review = userTask("review");
        review.setListenerConfig("{\"listeners\":[{\"eventType\":\"TASK_CREATED\","
                + "\"handlerId\":\"missingHandler\"}]}");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), review, end("end")),
                edges(edge("e1", "start", "review"), edge("e2", "review", "end")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_TASK_ACTION_RULE_INVALID,
                "review", null);
    }

    @Test
    void rejectsListenerConfigThatIsNotJsonObject() {
        ProcessNodeDTO review = userTask("review");
        review.setListenerConfig("[{\"taskActionRules\":{}}]");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), review, end("end")),
                edges(edge("e1", "start", "review"), edge("e2", "review", "end")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_TASK_ACTION_RULE_INVALID,
                "review", null);
    }

    private static ProcessDefinitionDetailDTO definition(java.util.List<ProcessNodeDTO> nodes,
                                                         java.util.List<ProcessEdgeDTO> edges) {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setNodes(nodes);
        definition.setEdges(edges);
        return definition;
    }

    private static java.util.List<ProcessNodeDTO> nodes(ProcessNodeDTO... nodes) {
        return Arrays.asList(nodes);
    }

    private static java.util.List<ProcessEdgeDTO> edges(ProcessEdgeDTO... edges) {
        return Arrays.asList(edges);
    }

    private static ProcessNodeDTO start(String code) {
        return node(code, NodeTypeEnum.START);
    }

    private static ProcessNodeDTO end(String code) {
        return node(code, NodeTypeEnum.END);
    }

    private static ProcessNodeDTO userTask(String code) {
        ProcessNodeDTO node = node(code, NodeTypeEnum.USER_TASK);
        node.setApproverRuleType(ApproverRuleTypeEnum.USER);
        node.setApproverRuleConfig("{\"userIds\":[\"u1\"]}");
        return node;
    }

    private static ProcessNodeDTO node(String code, NodeTypeEnum nodeType) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeName(code);
        node.setNodeType(nodeType);
        return node;
    }

    private static ProcessEdgeDTO edge(String code, String sourceCode, String targetCode) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(code);
        edge.setSourceNodeCode(sourceCode);
        edge.setTargetNodeCode(targetCode);
        edge.setDefaultEdge(Boolean.FALSE);
        return edge;
    }

    private static ProcessEdgeDTO conditionalEdge(String code,
                                                  String sourceCode,
                                                  String targetCode,
                                                  String conditionExpression) {
        ProcessEdgeDTO edge = edge(code, sourceCode, targetCode);
        edge.setConditionExpression(conditionExpression);
        return edge;
    }

    private static ProcessEdgeDTO defaultEdge(String code, String sourceCode, String targetCode) {
        ProcessEdgeDTO edge = edge(code, sourceCode, targetCode);
        edge.setDefaultEdge(Boolean.TRUE);
        return edge;
    }

    private static void assertHasIssue(ValidationResult result,
                                       String expectedCode,
                                       String expectedNodeCode,
                                       String expectedEdgeCode) {
        for (ValidationResult.Issue issue : result.getIssues()) {
            if (expectedCode.equals(issue.getCode())
                    && equals(expectedNodeCode, issue.getNodeCode())
                    && equals(expectedEdgeCode, issue.getEdgeCode())) {
                return;
            }
        }
        throw new AssertionError("Expected issue " + expectedCode + " for node "
                + expectedNodeCode + " and edge " + expectedEdgeCode + ".");
    }

    private static boolean equals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
