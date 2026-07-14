package com.flowmind.platform.api.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessDefinitionDtoContractTest {

    @Test
    void detailDtoStartsWithIndependentEmptyCollections() {
        ProcessDefinitionDetailDTO first = new ProcessDefinitionDetailDTO();
        ProcessDefinitionDetailDTO second = new ProcessDefinitionDetailDTO();

        assertTrue(first.getNodes().isEmpty());
        assertTrue(first.getEdges().isEmpty());
        assertTrue(first.getFormFields().isEmpty());
        assertTrue(first.getAttachmentTemplates().isEmpty());
        assertNotNull(first.getNodes());
        assertTrue(first.getNodes() != second.getNodes());
    }

    @Test
    void detailDtoCarriesDefinitionAndAllDefinitionParts() {
        ProcessDefinitionDetailDTO detail = new ProcessDefinitionDetailDTO();
        detail.setId("definition-001");
        detail.setProcessCode("expense");

        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setDefinitionId("definition-001");
        node.setNodeCode("review");
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setDefinitionId("definition-001");
        edge.setSourceNodeCode("start");
        ProcessFormFieldDTO field = new ProcessFormFieldDTO();
        field.setDefinitionId("definition-001");
        field.setFieldCode("amount");
        ProcessAttachmentTemplateDTO attachment = new ProcessAttachmentTemplateDTO();
        attachment.setDefinitionId("definition-001");
        attachment.setAttachmentCode("receipt");

        detail.setNodes(Arrays.asList(node));
        detail.setEdges(Arrays.asList(edge));
        detail.setFormFields(Arrays.asList(field));
        detail.setAttachmentTemplates(Arrays.asList(attachment));

        assertEquals("expense", detail.getProcessCode());
        assertEquals(node, detail.getNodes().get(0));
        assertEquals(edge, detail.getEdges().get(0));
        assertEquals(field, detail.getFormFields().get(0));
        assertEquals(attachment, detail.getAttachmentTemplates().get(0));
    }
}
