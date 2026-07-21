package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionModelValidatorM1Test {

    private final DefinitionModelValidator validator = new DefinitionModelValidator();

    @Test
    void requiresExactlyOneResolvableOutgoingEdgeForStartAndUserTask() {
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), userTask("review"), end("end")),
                edges(edge("e1", "start", "review"), edge("e2", "start", "end"),
                        edge("e3", "review", "missing")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_NODE_OUTGOING_EDGE_INVALID,
                "start", null);
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_NODE_OUTGOING_EDGE_INVALID,
                "review", null);
    }

    @Test
    void rejectsBlankEdgeCodeWithoutThrowing() {
        ProcessEdgeDTO blankCode = edge(null, "start", "end");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), end("end")), edges(blankCode));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID);
    }

    @Test
    void rejectsApproverAndMultiInstanceConfigurationOnGateway() {
        ProcessNodeDTO gateway = node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY);
        gateway.setApproverRuleType(ApproverRuleTypeEnum.USER);
        gateway.setApproverRuleConfig("{\"userIds\":[\"u1\"]}");
        gateway.setMultiInstanceMode(MultiInstanceModeEnum.SINGLE);
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), gateway, end("end")),
                edges(edge("e1", "start", "route"), edge("e2", "route", "end")));

        ValidationResult result = validator.validate(definition);

        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_GATEWAY_NODE_CONFIGURATION_INVALID,
                "route", null);
    }

    @Test
    void rejectsExclusiveGatewayWithMissingConditionOnNonDefaultEdge() {
        ProcessNodeDTO gateway = node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY);
        ProcessEdgeDTO missingCondition = edge("e2", "route", "approve");
        ProcessEdgeDTO defaultEdge = edge("e3", "route", "reject");
        defaultEdge.setDefaultEdge(Boolean.TRUE);
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), gateway, userTask("approve"), userTask("reject"), end("end")),
                edges(edge("e1", "start", "route"), missingCondition, defaultEdge,
                        edge("e4", "approve", "end"), edge("e5", "reject", "end")));

        ValidationResult result = validator.validate(definition);

        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_EXCLUSIVE_GATEWAY_TOPOLOGY_INVALID,
                "route", "e2");
    }

    @Test
    void rejectsParallelBranchThatCanBypassItsPairedJoin() {
        ProcessNodeDTO split = node("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY);
        split.setPairedGatewayCode("join");
        ProcessNodeDTO join = node("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY);
        join.setPairedGatewayCode("split");
        ProcessEdgeDTO conditionalBranch = edge("branch-a", "split", "a");
        conditionalBranch.setConditionExpression("amount > 0");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), split, userTask("a"), userTask("b"), join, end("end")),
                edges(edge("e1", "start", "split"), conditionalBranch,
                        edge("branch-b", "split", "b"), edge("e2", "a", "join"),
                        edge("e3", "b", "end"), edge("e4", "join", "end")));

        ValidationResult result = validator.validate(definition);

        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_TOPOLOGY_INVALID,
                "split", "branch-a");
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_TOPOLOGY_INVALID,
                "split", "branch-b");
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_TOPOLOGY_INVALID,
                "join", null);
    }

    @Test
    void rejectsParallelBranchWithCycleBeforePairedJoin() {
        ProcessNodeDTO split = node("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY);
        split.setPairedGatewayCode("join");
        ProcessNodeDTO loop = node("loop", NodeTypeEnum.EXCLUSIVE_GATEWAY);
        ProcessNodeDTO join = node("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY);
        join.setPairedGatewayCode("split");
        ProcessEdgeDTO loopToSelf = edge("loop-self", "loop", "loop");
        loopToSelf.setConditionExpression("retry");
        ProcessEdgeDTO loopToJoin = edge("loop-join", "loop", "join");
        loopToJoin.setConditionExpression("done");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), split, loop, userTask("b"), join, end("end")),
                edges(edge("e1", "start", "split"), edge("branch-loop", "split", "loop"),
                        edge("branch-b", "split", "b"), loopToSelf, loopToJoin,
                        edge("e2", "b", "join"), edge("e3", "join", "end")));

        ValidationResult result = validator.validate(definition);

        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_TOPOLOGY_INVALID,
                "split", "branch-loop");
    }

    @Test
    void rejectsExclusiveAndParallelGatewayCardinalityViolations() {
        ProcessNodeDTO split = node("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY);
        split.setPairedGatewayCode("join");
        ProcessNodeDTO join = node("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY);
        join.setPairedGatewayCode("split");
        ProcessNodeDTO exclusive = node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY);
        ProcessEdgeDTO onlyExclusiveEdge = edge("e2", "route", "split");
        onlyExclusiveEdge.setDefaultEdge(Boolean.TRUE);
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), exclusive, split, userTask("review"), join,
                        end("end-a"), end("end-b")),
                edges(edge("e1", "start", "route"), onlyExclusiveEdge,
                        edge("branch", "split", "review"), edge("e3", "review", "join"),
                        edge("e4", "join", "end-a"), edge("e5", "join", "end-b")));

        ValidationResult result = validator.validate(definition);

        assertHasMessage(result, "Exclusive gateway must have at least two outgoing edges.", "route");
        assertHasMessage(result, "Parallel split gateway must have at least two outgoing branches.",
                "split");
        assertHasMessage(result, "Parallel join gateway must have at least two incoming branches.",
                "join");
        assertHasMessage(result, "Parallel join gateway must have exactly one outgoing edge.", "join");
    }

    @Test
    void rejectsGraphCycleEvenWhenItHasAnExitToEnd() {
        ProcessNodeDTO gateway = node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY);
        ProcessEdgeDTO retry = edge("retry", "route", "route");
        retry.setConditionExpression("retry");
        ProcessEdgeDTO finish = edge("finish", "route", "end");
        finish.setConditionExpression("done");
        ProcessDefinitionDetailDTO definition = definition(
                nodes(start("start"), gateway, end("end")),
                edges(edge("e1", "start", "route"), retry, finish));

        ValidationResult result = validator.validate(definition);

        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_GRAPH_CYCLE_INVALID, null, null);
    }

    @Test
    void acceptsValidFormAndAttachmentConfiguration() {
        ProcessDefinitionDetailDTO definition = linearDefinition();
        definition.setFormFields(Collections.singletonList(formField("amount")));
        definition.setAttachmentTemplates(Collections.singletonList(
                attachment("cfg-1", "tpl-1", "receipt", true, 1, 3, "review")));

        ValidationResult result = validator.validate(definition);

        assertTrue(result.isValid());
    }

    @Test
    void rejectsInvalidFormAndAttachmentConfiguration() {
        ProcessDefinitionDetailDTO definition = linearDefinition();
        definition.setFormFields(Arrays.asList(formField("amount"), formField("amount")));
        definition.setAttachmentTemplates(Arrays.asList(
                attachment("cfg-1", "tpl-1", "receipt", false, 5, 3, "missing"),
                attachment("cfg-1", "tpl-1", "contract", true, 0, 2, "review"),
                attachment("cfg-1", "tpl-3", "receipt", false, 0, 2, "review")));

        ValidationResult result = validator.validate(definition);

        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.MODEL_FORM_FIELD_INVALID);
        assertContainsCode(result, FrozenValidationErrorCodes.MODEL_ATTACHMENT_CONFIGURATION_INVALID);
        assertHasIssue(result, FrozenValidationErrorCodes.MODEL_ATTACHMENT_CONFIGURATION_INVALID,
                "missing", null);
    }

    @Test
    void acceptsNullExtensionCollections() {
        ProcessDefinitionDetailDTO definition = linearDefinition();
        definition.setFormFields(null);
        definition.setAttachmentTemplates(null);

        ValidationResult result = validator.validate(definition);

        assertTrue(result.isValid());
    }

    private static ProcessDefinitionDetailDTO linearDefinition() {
        return definition(nodes(start("start"), userTask("review"), end("end")),
                edges(edge("e1", "start", "review"), edge("e2", "review", "end")));
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
        ProcessNodeDTO userTask = node(code, NodeTypeEnum.USER_TASK);
        userTask.setApproverRuleType(ApproverRuleTypeEnum.USER);
        userTask.setApproverRuleConfig("{\"userIds\":[\"u1\"]}");
        return userTask;
    }

    private static ProcessNodeDTO node(String code, NodeTypeEnum type) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeType(type);
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

    private static ProcessFormFieldDTO formField(String code) {
        ProcessFormFieldDTO formField = new ProcessFormFieldDTO();
        formField.setFieldCode(code);
        return formField;
    }

    private static ProcessAttachmentTemplateDTO attachment(String configId,
                                                           String templateId,
                                                           String attachmentCode,
                                                           boolean required,
                                                           int minCount,
                                                           int maxCount,
                                                           String applicableNodeCode) {
        ProcessAttachmentTemplateDTO attachment = new ProcessAttachmentTemplateDTO();
        attachment.setAttachmentConfigId(configId);
        attachment.setAttachmentTemplateId(templateId);
        attachment.setAttachmentCode(attachmentCode);
        attachment.setRequired(required);
        attachment.setMinCount(minCount);
        attachment.setMaxCount(maxCount);
        attachment.setApplicableNodeCodes(Collections.singletonList(applicableNodeCode));
        return attachment;
    }

    private static void assertContainsCode(ValidationResult result, String expectedCode) {
        for (ValidationResult.Issue issue : result.getIssues()) {
            if (expectedCode.equals(issue.getCode())) {
                return;
            }
        }
        throw new AssertionError("Expected issue code " + expectedCode + " but got "
                + result.getIssues());
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

    private static void assertHasMessage(ValidationResult result,
                                         String expectedMessage,
                                         String expectedNodeCode) {
        for (ValidationResult.Issue issue : result.getIssues()) {
            if (expectedMessage.equals(issue.getMessage())
                    && equals(expectedNodeCode, issue.getNodeCode())) {
                return;
            }
        }
        throw new AssertionError("Expected issue message " + expectedMessage + " for node "
                + expectedNodeCode + ".");
    }

    private static boolean equals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
