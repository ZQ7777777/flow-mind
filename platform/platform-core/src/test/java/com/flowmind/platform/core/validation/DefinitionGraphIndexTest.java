package com.flowmind.platform.core.validation;

import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.dto.ValidationResult;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionGraphIndexTest {

    @Test
    void buildsStableReadOnlyIndexesWithoutChangingInputOrder() {
        ProcessNodeDTO end = node("end", NodeTypeEnum.END, 30);
        ProcessNodeDTO start = node("start", NodeTypeEnum.START, 10);
        ProcessNodeDTO gateway = node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY, 20);
        ProcessEdgeDTO toEnd = edge("e2", "route", "end", 20);
        ProcessEdgeDTO toGateway = edge("e1", "start", "route", 10);
        ProcessDefinitionDetailDTO definition = definition(
                Arrays.asList(end, start, gateway), Arrays.asList(toEnd, toGateway));

        DefinitionGraphIndex graph = DefinitionGraphIndex.from(definition);

        assertEquals(Arrays.asList("start", "route", "end"), nodeCodes(graph));
        assertEquals(Arrays.asList("e1", "e2"), edgeCodes(graph));
        assertEquals("e1", graph.getOutgoingEdges("start").get(0).getEdgeCode());
        assertEquals("e2", graph.getIncomingEdges("end").get(0).getEdgeCode());
        assertEquals("route", graph.getGatewayNodes().get(0).getNodeCode());
        assertEquals("end", definition.getNodes().get(0).getNodeCode());
        assertThrows(UnsupportedOperationException.class,
                () -> graph.getNodes().add(node("extra", NodeTypeEnum.USER_TASK, 40)));
        assertThrows(UnsupportedOperationException.class,
                () -> graph.getNodesByCode().put("extra", start));
        assertThrows(UnsupportedOperationException.class,
                () -> graph.getOutgoingEdges("start").add(toGateway));
    }

    @Test
    void acceptsNullCollectionsAndLetsValidatorReportNullElements() {
        ProcessDefinitionDetailDTO emptyDefinition = new ProcessDefinitionDetailDTO();
        emptyDefinition.setNodes(null);
        emptyDefinition.setEdges(null);

        DefinitionGraphIndex emptyGraph = DefinitionGraphIndex.from(emptyDefinition);

        assertTrue(emptyGraph.getNodes().isEmpty());
        assertTrue(emptyGraph.getEdges().isEmpty());

        ProcessDefinitionDetailDTO malformedDefinition = definition(
                Arrays.asList(null, node("start", NodeTypeEnum.START, 10),
                        node("end", NodeTypeEnum.END, 20)),
                Arrays.asList(null, edge("e1", "start", "end", 10)));

        DefinitionGraphIndex malformedGraph = DefinitionGraphIndex.from(malformedDefinition);
        ValidationResult result = new DefinitionModelValidator().validate(malformedDefinition);

        assertEquals(1, malformedGraph.getNullNodeCount());
        assertEquals(1, malformedGraph.getNullEdgeCount());
        assertFalse(result.isValid());
        assertContainsCode(result, FrozenValidationErrorCodes.MODEL_NODE_REFERENCE_INVALID);
        assertContainsCode(result, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID);
    }

    @Test
    void keepsDirectionalAdjacencyForEdgesWithOneMissingEndpoint() {
        ProcessNodeDTO start = node("start", NodeTypeEnum.START, 10);
        ProcessNodeDTO gateway = node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY, 20);
        ProcessNodeDTO end = node("end", NodeTypeEnum.END, 30);
        ProcessEdgeDTO startToGateway = edge("e1", "start", "route", 10);
        ProcessEdgeDTO validDefault = edge("e2", "route", "end", 20);
        validDefault.setDefaultEdge(true);
        ProcessEdgeDTO missingTargetDefault = edge("e3", "route", "missing", 30);
        missingTargetDefault.setDefaultEdge(true);
        ProcessDefinitionDetailDTO definition = definition(
                Arrays.asList(start, gateway, end),
                Arrays.asList(startToGateway, validDefault, missingTargetDefault));

        DefinitionGraphIndex graph = DefinitionGraphIndex.from(definition);
        ValidationResult result = new DefinitionModelValidator().validate(definition);

        assertEquals(2, graph.getOutgoingEdges("route").size());
        assertContainsCode(result, FrozenValidationErrorCodes.MODEL_EDGE_REFERENCE_INVALID);
        assertContainsCode(result, FrozenValidationErrorCodes.MODEL_GATEWAY_DEFAULT_EDGE_INVALID);
    }

    @Test
    void keepsInvalidModelIssueOrderStableAcrossInputOrder() {
        ProcessDefinitionDetailDTO first = invalidDefinition(false);
        ProcessDefinitionDetailDTO reordered = invalidDefinition(true);

        DefinitionGraphIndex graph = DefinitionGraphIndex.from(first);
        ValidationResult firstResult = new DefinitionModelValidator().validate(first);
        ValidationResult reorderedResult = new DefinitionModelValidator().validate(reordered);

        assertEquals(NodeTypeEnum.USER_TASK, graph.getNodesByCode().get("route").getNodeType());
        assertEquals(issueSignatures(firstResult), issueSignatures(reorderedResult));
    }

    private static ProcessDefinitionDetailDTO invalidDefinition(boolean reversed) {
        ProcessNodeDTO start = node("start", NodeTypeEnum.START, 10);
        ProcessNodeDTO routeGateway = node("route", NodeTypeEnum.EXCLUSIVE_GATEWAY, 20);
        ProcessNodeDTO routeUserTask = node("route", NodeTypeEnum.USER_TASK, 20);
        routeUserTask.setApproverRuleType(null);
        routeUserTask.setApproverRuleConfig(null);
        ProcessNodeDTO end = node("end", NodeTypeEnum.END, 30);
        ProcessEdgeDTO startToRoute = edge("e1", "start", "route", 10);
        ProcessEdgeDTO firstDefault = edge("e2", "route", "end", 20);
        firstDefault.setDefaultEdge(true);
        ProcessEdgeDTO secondDefault = edge("e3", "route", "missing", 30);
        secondDefault.setDefaultEdge(true);
        List<ProcessNodeDTO> nodes = Arrays.asList(start, routeGateway, routeUserTask, end);
        List<ProcessEdgeDTO> edges = Arrays.asList(startToRoute, firstDefault, secondDefault);
        if (reversed) {
            nodes = Arrays.asList(end, routeUserTask, routeGateway, start);
            edges = Arrays.asList(secondDefault, firstDefault, startToRoute);
        }
        return definition(nodes, edges);
    }

    private static ProcessDefinitionDetailDTO definition(List<ProcessNodeDTO> nodes,
                                                         List<ProcessEdgeDTO> edges) {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setNodes(nodes);
        definition.setEdges(edges);
        return definition;
    }

    private static ProcessNodeDTO node(String code, NodeTypeEnum type, int sortOrder) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeType(type);
        node.setSortOrder(sortOrder);
        if (NodeTypeEnum.USER_TASK.equals(type)) {
            node.setApproverRuleType(ApproverRuleTypeEnum.USER);
            node.setApproverRuleConfig("{\"userIds\":[\"u1\"]}");
        }
        return node;
    }

    private static ProcessEdgeDTO edge(String code, String source, String target, int sortOrder) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(code);
        edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target);
        edge.setSortOrder(sortOrder);
        return edge;
    }

    private static List<String> nodeCodes(DefinitionGraphIndex graph) {
        List<String> codes = new java.util.ArrayList<String>();
        for (ProcessNodeDTO node : graph.getNodes()) {
            codes.add(node.getNodeCode());
        }
        return codes;
    }

    private static List<String> edgeCodes(DefinitionGraphIndex graph) {
        List<String> codes = new java.util.ArrayList<String>();
        for (ProcessEdgeDTO edge : graph.getEdges()) {
            codes.add(edge.getEdgeCode());
        }
        return codes;
    }

    private static void assertContainsCode(ValidationResult result, String expectedCode) {
        for (ValidationResult.Issue issue : result.getIssues()) {
            if (expectedCode.equals(issue.getCode())) {
                return;
            }
        }
        throw new AssertionError("Expected issue code " + expectedCode + " but got "
                + Collections.singletonList(result.getIssues()));
    }

    private static List<String> issueSignatures(ValidationResult result) {
        List<String> signatures = new java.util.ArrayList<String>();
        for (ValidationResult.Issue issue : result.getIssues()) {
            signatures.add(issue.getCode() + "|" + issue.getNodeCode() + "|"
                    + issue.getEdgeCode() + "|" + issue.getMessage());
        }
        return signatures;
    }
}
