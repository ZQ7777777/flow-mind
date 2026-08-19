package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionModelValidatorTest {

    private final DefinitionModelValidator validator = new DefinitionModelValidator();

    @Test
    void validLinearModelPasses() {
        ValidationResult result = validator.validate(linearDefinition());

        assertTrue(result.isValid());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void validParallelGatewayPairPasses() {
        ValidationResult result = validator.validate(validParallelGatewayPair());

        assertTrue(result.isValid());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void validStarterNoticeNodePasses() {
        ProcessNodeDTO notice = node("notify", NodeTypeEnum.NOTICE);
        notice.setApproverRuleType(ApproverRuleTypeEnum.STARTER);
        notice.setNoticeConfig("{\"title\":\"流程知会\",\"content\":\"办理完成\"}");
        ValidationResult result = validator.validate(definition(
                nodes(node("start", NodeTypeEnum.START), userTask("review"), notice,
                        node("end", NodeTypeEnum.END)),
                edges(edge("e1", "start", "review"), edge("e2", "review", "notify"),
                        edge("e3", "notify", "end"))));

        assertTrue(result.isValid());
    }

    @Test
    void noticeConfigRejectsNullText() {
        ProcessNodeDTO notice = node("notify", NodeTypeEnum.NOTICE);
        notice.setApproverRuleType(ApproverRuleTypeEnum.STARTER);
        notice.setNoticeConfig("{\"title\":null}");
        ValidationResult result = validator.validate(definition(
                nodes(node("start", NodeTypeEnum.START), notice, node("end", NodeTypeEnum.END)),
                edges(edge("e1", "start", "notify"), edge("e2", "notify", "end"))));

        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.MODEL_NOTICE_CONFIGURATION_INVALID);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDefinitions")
    void invalidModelsAreRejectedWithFrozenErrorCode(ModelFixture fixture) {
        ValidationResult result = validator.validate(fixture.definition);

        assertFalse(result.isValid());
        assertContainsCode(result, fixture.expectedCode);
    }

    private static Stream<ModelFixture> invalidDefinitions() {
        return Stream.of(
                fixture("missing start", definition(
                                nodes(userTask("review"), node("end", NodeTypeEnum.END)),
                                edges(edge("e1", "review", "end"))),
                        FrozenValidationErrorCodes.MODEL_START_NODE_INVALID),
                fixture("two starts", definition(
                                nodes(node("start-a", NodeTypeEnum.START), node("start-b", NodeTypeEnum.START), userTask("review"), node("end", NodeTypeEnum.END)),
                                edges(edge("e1", "start-a", "review"), edge("e2", "review", "end"))),
                        FrozenValidationErrorCodes.MODEL_START_NODE_INVALID),
                fixture("missing end", definition(
                                nodes(node("start", NodeTypeEnum.START), userTask("review")),
                                edges(edge("e1", "start", "review"))),
                        FrozenValidationErrorCodes.MODEL_END_NODE_REQUIRED),
                fixture("edge references missing target", definition(
                                nodes(node("start", NodeTypeEnum.START), userTask("review"), node("end", NodeTypeEnum.END)),
                                edges(edge("e1", "start", "review"), edge("e2", "review", "missing"))),
                        FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID),
                fixture("user task without approver", definition(
                                nodes(node("start", NodeTypeEnum.START), node("review", NodeTypeEnum.USER_TASK), node("end", NodeTypeEnum.END)),
                                edges(edge("e1", "start", "review"), edge("e2", "review", "end"))),
                        FrozenValidationErrorCodes.MODEL_USER_TASK_APPROVER_REQUIRED),
                fixture("notice without recipient", definition(
                                nodes(node("start", NodeTypeEnum.START), node("notify", NodeTypeEnum.NOTICE),
                                        node("end", NodeTypeEnum.END)),
                                edges(edge("e1", "start", "notify"), edge("e2", "notify", "end"))),
                        FrozenValidationErrorCodes.MODEL_NOTICE_CONFIGURATION_INVALID),
                fixture("exclusive gateway has two defaults", exclusiveGatewayWithTwoDefaults(),
                        FrozenValidationErrorCodes.MODEL_GATEWAY_DEFAULT_EDGE_INVALID),
                fixture("parallel gateway pair invalid", invalidParallelGatewayPair(),
                        FrozenValidationErrorCodes.MODEL_PARALLEL_GATEWAY_PAIR_INVALID),
                fixture("isolated node", definition(
                                nodes(node("start", NodeTypeEnum.START), userTask("review"), node("end", NodeTypeEnum.END), userTask("isolated")),
                                edges(edge("e1", "start", "review"), edge("e2", "review", "end"))),
                        FrozenValidationErrorCodes.MODEL_ORPHAN_NODE_INVALID));
    }

    private static ProcessDefinitionDetailDTO linearDefinition() {
        return definition(
                nodes(node("start", NodeTypeEnum.START), userTask("review"), node("end", NodeTypeEnum.END)),
                edges(edge("e1", "start", "review"), edge("e2", "review", "end")));
    }

    private static ProcessDefinitionDetailDTO exclusiveGatewayWithTwoDefaults() {
        ProcessEdgeDTO toApprove = edge("e2", "route", "approve");
        toApprove.setDefaultEdge(Boolean.TRUE);
        ProcessEdgeDTO toReject = edge("e3", "route", "reject");
        toReject.setDefaultEdge(Boolean.TRUE);
        return definition(
                nodes(node("start", NodeTypeEnum.START), node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY),
                        userTask("approve"), userTask("reject"), node("end", NodeTypeEnum.END)),
                edges(edge("e1", "start", "route"), toApprove, toReject,
                        edge("e4", "approve", "end"), edge("e5", "reject", "end")));
    }

    private static ProcessDefinitionDetailDTO invalidParallelGatewayPair() {
        ProcessNodeDTO split = node("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY);
        split.setPairedGatewayCode("join");
        ProcessNodeDTO join = node("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY);
        join.setPairedGatewayCode("other-split");
        return definition(
                nodes(node("start", NodeTypeEnum.START), split, userTask("a"), userTask("b"), join, node("end", NodeTypeEnum.END)),
                edges(edge("e1", "start", "split"), edge("e2", "split", "a"),
                        edge("e3", "split", "b"), edge("e4", "a", "join"),
                        edge("e5", "b", "join"), edge("e6", "join", "end")));
    }

    private static ProcessDefinitionDetailDTO validParallelGatewayPair() {
        ProcessNodeDTO split = node("split", NodeTypeEnum.PARALLEL_SPLIT_GATEWAY);
        split.setPairedGatewayCode("join");
        ProcessNodeDTO join = node("join", NodeTypeEnum.PARALLEL_JOIN_GATEWAY);
        join.setPairedGatewayCode("split");
        return definition(
                nodes(node("start", NodeTypeEnum.START), split, userTask("a"), userTask("b"), join, node("end", NodeTypeEnum.END)),
                edges(edge("e1", "start", "split"), edge("e2", "split", "a"),
                        edge("e3", "split", "b"), edge("e4", "a", "join"),
                        edge("e5", "b", "join"), edge("e6", "join", "end")));
    }

    private static ProcessDefinitionDetailDTO definition(List<ProcessNodeDTO> nodes,
                                                         List<ProcessEdgeDTO> edges) {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setNodes(nodes);
        definition.setEdges(edges);
        return definition;
    }

    private static List<ProcessNodeDTO> nodes(ProcessNodeDTO... nodes) {
        return Arrays.asList(nodes);
    }

    private static List<ProcessEdgeDTO> edges(ProcessEdgeDTO... edges) {
        return Arrays.asList(edges);
    }

    private static ProcessNodeDTO node(String nodeCode, NodeTypeEnum nodeType) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(nodeCode);
        node.setNodeName(nodeCode);
        node.setNodeType(nodeType);
        return node;
    }

    private static ProcessNodeDTO userTask(String nodeCode) {
        ProcessNodeDTO node = node(nodeCode, NodeTypeEnum.USER_TASK);
        node.setApproverRuleType(ApproverRuleTypeEnum.USER);
        node.setApproverRuleConfig("{\"userIds\":[\"u1\"]}");
        return node;
    }

    private static ProcessEdgeDTO edge(String edgeCode, String sourceNodeCode, String targetNodeCode) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(edgeCode);
        edge.setSourceNodeCode(sourceNodeCode);
        edge.setTargetNodeCode(targetNodeCode);
        edge.setDefaultEdge(Boolean.FALSE);
        return edge;
    }

    private static ModelFixture fixture(String name,
                                        ProcessDefinitionDetailDTO definition,
                                        String expectedCode) {
        return new ModelFixture(name, definition, expectedCode);
    }

    private void assertContainsCode(ValidationResult result, String expectedCode) {
        for (ValidationResult.Issue issue : result.getIssues()) {
            if (expectedCode.equals(issue.getCode())) {
                return;
            }
        }
        throw new AssertionError("Expected issue code " + expectedCode
                + " but got " + result.getIssues());
    }

    private static final class ModelFixture {
        private final String name;
        private final ProcessDefinitionDetailDTO definition;
        private final String expectedCode;

        private ModelFixture(String name,
                             ProcessDefinitionDetailDTO definition,
                             String expectedCode) {
            this.name = name;
            this.definition = definition;
            this.expectedCode = expectedCode;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
