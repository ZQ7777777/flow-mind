package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import com.flowmind.platform.persistence.entity.ProcessEdgeEntity;
import com.flowmind.platform.persistence.entity.ProcessNodeEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DefinitionGraphDraftFactoryTest {

    private static final String ACTION_RULES = "{\"taskActionRules\":{\"reject\":{\"enabled\":true,"
            + "\"targetNodeCodes\":[\"apply\"]},\"directSend\":{\"enabled\":true,"
            + "\"targetMode\":\"REJECT_SOURCE\"}}}";

    @Test
    void normalizesM5NodeAndEdgeConfigurationWithoutLosingRawJson() {
        DefinitionGraphDraftFactory factory = new DefinitionGraphDraftFactory(sequenceIds());
        ProcessNodeDTO review = node("review");
        review.setMultiInstanceMode(MultiInstanceModeEnum.OR_SIGN);
        review.setListenerConfig(ACTION_RULES);
        ProcessEdgeDTO route = edge("route-review", "route", "review");
        route.setConditionExpression("amount >= 1000");

        List<ProcessNodeDTO> nodes = factory.normalizeNodes("definition-new", Arrays.asList(review));
        List<ProcessEdgeDTO> edges = factory.normalizeEdges("definition-new", Arrays.asList(route));

        assertEquals("definition-new", nodes.get(0).getDefinitionId());
        assertEquals(MultiInstanceModeEnum.OR_SIGN, nodes.get(0).getMultiInstanceMode());
        assertEquals(ACTION_RULES, nodes.get(0).getListenerConfig());
        assertEquals("amount >= 1000", edges.get(0).getConditionExpression());
    }

    @Test
    void copiesM5NodeAndEdgeConfigurationToNewDefinition() {
        DefinitionGraphDraftFactory factory = new DefinitionGraphDraftFactory(sequenceIds());
        ProcessNodeEntity sourceNode = nodeEntity("node-source", "definition-old", "review");
        sourceNode.setMultiInstanceMode(MultiInstanceModeEnum.COUNTERSIGN.name());
        sourceNode.setListenerConfig(ACTION_RULES);
        ProcessEdgeEntity sourceEdge = edgeEntity("edge-source", "definition-old", "route-review");
        sourceEdge.setConditionExpression("amount >= 1000");

        List<ProcessNodeEntity> nodes = factory.copyNodeEntities(Arrays.asList(sourceNode), "definition-new");
        List<ProcessEdgeEntity> edges = factory.copyEdgeEntities(Arrays.asList(sourceEdge), "definition-new");

        assertNotEquals(sourceNode.getId(), nodes.get(0).getId());
        assertEquals("definition-new", nodes.get(0).getDefinitionId());
        assertEquals(MultiInstanceModeEnum.COUNTERSIGN.name(), nodes.get(0).getMultiInstanceMode());
        assertEquals(ACTION_RULES, nodes.get(0).getListenerConfig());
        assertNotEquals(sourceEdge.getId(), edges.get(0).getId());
        assertEquals("definition-new", edges.get(0).getDefinitionId());
        assertEquals("amount >= 1000", edges.get(0).getConditionExpression());
    }

    private static ProcessNodeDTO node(String code) {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode(code);
        node.setNodeName(code);
        node.setNodeType(NodeTypeEnum.USER_TASK);
        return node;
    }

    private static ProcessEdgeDTO edge(String code, String source, String target) {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode(code);
        edge.setSourceNodeCode(source);
        edge.setTargetNodeCode(target);
        return edge;
    }

    private static ProcessNodeEntity nodeEntity(String id, String definitionId, String code) {
        ProcessNodeEntity node = new ProcessNodeEntity();
        node.setId(id);
        node.setDefinitionId(definitionId);
        node.setNodeCode(code);
        node.setNodeName(code);
        node.setNodeType(NodeTypeEnum.USER_TASK.name());
        return node;
    }

    private static ProcessEdgeEntity edgeEntity(String id, String definitionId, String code) {
        ProcessEdgeEntity edge = new ProcessEdgeEntity();
        edge.setId(id);
        edge.setDefinitionId(definitionId);
        edge.setEdgeCode(code);
        edge.setSourceNodeCode("route");
        edge.setTargetNodeCode("review");
        return edge;
    }

    private static Supplier<String> sequenceIds() {
        final AtomicInteger sequence = new AtomicInteger(1);
        return new Supplier<String>() {
            @Override
            public String get() {
                return "generated-" + sequence.getAndIncrement();
            }
        };
    }
}
