package com.flowmind.platform.runtime.definition.port;

import com.flowmind.platform.api.dto.ProcessAttachmentTemplateDTO;
import com.flowmind.platform.api.dto.ProcessDefinitionDetailDTO;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessFormFieldDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.enums.ActivationStatusEnum;
import com.flowmind.platform.api.enums.ApproverRuleTypeEnum;
import com.flowmind.platform.api.enums.AttachmentConfigStatusEnum;
import com.flowmind.platform.api.enums.AttachmentTemplateStatusEnum;
import com.flowmind.platform.api.enums.DefinitionStatusEnum;
import com.flowmind.platform.api.enums.MultiInstanceModeEnum;
import com.flowmind.platform.api.enums.NodeTypeEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDefinitionInputTest {

    @Test
    void completeOfficialDefinitionCanBeLoadedThroughSingleReadPort() {
        ProcessDefinitionDetailDTO definition = completeDefinition();
        RuntimeDefinitionInput expected = RuntimeDefinitionInput.from(definition);
        RuntimeDefinitionReadPort port = definitionId -> {
            assertEquals("definition-1", definitionId);
            return expected;
        };

        RuntimeDefinitionInput actual = port.loadDefinition("definition-1");

        assertEquals("definition-1", actual.getDefinitionId());
        assertEquals("generic-approval", actual.getProcessCode());
        assertEquals(Integer.valueOf(3), actual.getVersion());
        assertEquals(DefinitionStatusEnum.PUBLISHED, actual.getDefinitionStatus());
        assertEquals(ActivationStatusEnum.ACTIVE, actual.getActivationStatus());
        assertEquals(definition.getNodes(), actual.getNodes());
        assertEquals(definition.getEdges(), actual.getEdges());
        assertEquals(definition.getFormFields(), actual.getFormFields());
        assertEquals(definition.getAttachmentTemplates(), actual.getAttachmentTemplates());

        ProcessNodeDTO node = actual.getNodes().get(0);
        assertEquals("review", node.getNodeCode());
        assertEquals("Review", node.getNodeName());
        assertEquals(NodeTypeEnum.USER_TASK, node.getNodeType());
        assertEquals("join", node.getPairedGatewayCode());
        assertEquals(ApproverRuleTypeEnum.ROLE, node.getApproverRuleType());
        assertEquals("{\"roleCodes\":[\"reviewer\"]}", node.getApproverRuleConfig());
        assertEquals(MultiInstanceModeEnum.COUNTERSIGN, node.getMultiInstanceMode());
        assertEquals("{\"onComplete\":\"audit\"}", node.getListenerConfig());
        assertEquals("{\"hours\":24}", node.getTimeoutConfig());
        assertEquals("{\"minutes\":30}", node.getReminderConfig());
        assertEquals(Double.valueOf(120.5D), node.getPositionX());
        assertEquals(Double.valueOf(240.5D), node.getPositionY());
        assertEquals(Integer.valueOf(10), node.getSortOrder());

        ProcessEdgeDTO edge = actual.getEdges().get(0);
        assertEquals("edge-review-end", edge.getEdgeCode());
        assertEquals("review", edge.getSourceNodeCode());
        assertEquals("end", edge.getTargetNodeCode());
        assertEquals("${approved == true}", edge.getConditionExpression());
        assertEquals(Boolean.FALSE, edge.getDefaultEdge());
        assertEquals(Integer.valueOf(20), edge.getSortOrder());

        assertEquals("amount", actual.getFormFields().get(0).getFieldCode());

        ProcessAttachmentTemplateDTO attachment = actual.getAttachmentTemplates().get(0);
        assertEquals("config-group-1", attachment.getAttachmentConfigId());
        assertEquals(AttachmentConfigStatusEnum.ACTIVE, attachment.getConfigStatus());
        assertEquals("template-version-2", attachment.getAttachmentTemplateId());
        assertEquals("supporting-document", attachment.getAttachmentCode());
        assertEquals(AttachmentTemplateStatusEnum.ENABLED, attachment.getTemplateStatus());
        assertEquals(Arrays.asList("review", "end"), attachment.getApplicableNodeCodes());
        assertEquals(Arrays.asList("pdf", "png"), attachment.getAllowedExtensions());
        assertEquals(Boolean.TRUE, attachment.getRequired());
        assertEquals(Integer.valueOf(1), attachment.getMinCount());
        assertEquals(Integer.valueOf(3), attachment.getMaxCount());
        assertEquals(Long.valueOf(10_485_760L), attachment.getMaxSizeBytes());
    }

    @Test
    void emptyCollectionsAreNormalizedForJava8Callers() {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId("definition-empty");
        definition.setProcessCode("empty");
        definition.setVersion(1);
        definition.setDefinitionStatus(DefinitionStatusEnum.DRAFT);
        definition.setActivationStatus(ActivationStatusEnum.INACTIVE);
        definition.setNodes(null);
        definition.setEdges(Collections.<ProcessEdgeDTO>emptyList());
        definition.setFormFields(null);
        definition.setAttachmentTemplates(Collections.<ProcessAttachmentTemplateDTO>emptyList());

        RuntimeDefinitionInput input = RuntimeDefinitionInput.from(definition);

        assertTrue(input.getNodes().isEmpty());
        assertTrue(input.getEdges().isEmpty());
        assertTrue(input.getFormFields().isEmpty());
        assertTrue(input.getAttachmentTemplates().isEmpty());
        assertEquals(DefinitionStatusEnum.DRAFT, input.getDefinitionStatus());
        assertEquals(ActivationStatusEnum.INACTIVE, input.getActivationStatus());
    }

    @Test
    void optionalNodeConfigurationCanRemainAbsent() {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode("end");
        node.setNodeType(NodeTypeEnum.END);
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setNodes(new ArrayList<ProcessNodeDTO>(Arrays.asList(node)));

        ProcessNodeDTO copiedNode = RuntimeDefinitionInput.from(definition).getNodes().get(0);

        assertNull(copiedNode.getPairedGatewayCode());
        assertNull(copiedNode.getApproverRuleType());
        assertNull(copiedNode.getApproverRuleConfig());
        assertNull(copiedNode.getMultiInstanceMode());
        assertNull(copiedNode.getListenerConfig());
        assertNull(copiedNode.getTimeoutConfig());
        assertNull(copiedNode.getReminderConfig());
    }

    @Test
    void sourceAndReturnedCollectionsCannotMutateInternalState() {
        ProcessDefinitionDetailDTO definition = completeDefinition();
        RuntimeDefinitionInput input = RuntimeDefinitionInput.from(definition);

        definition.getNodes().get(0).setNodeCode("changed-at-source");
        definition.getAttachmentTemplates().get(0).getApplicableNodeCodes().add("changed-at-source");
        ProcessNodeDTO returnedNode = input.getNodes().get(0);
        returnedNode.setNodeCode("changed-at-reader");
        input.getAttachmentTemplates().get(0).getApplicableNodeCodes().add("changed-at-reader");

        assertEquals("review", input.getNodes().get(0).getNodeCode());
        assertEquals(Arrays.asList("review", "end"),
                input.getAttachmentTemplates().get(0).getApplicableNodeCodes());
        assertThrows(UnsupportedOperationException.class,
                () -> input.getNodes().add(new ProcessNodeDTO()));
    }

    @Test
    void portDeclaresOnlyOneReadOperation() {
        Method[] methods = RuntimeDefinitionReadPort.class.getDeclaredMethods();

        assertEquals(1, methods.length);
        assertEquals("loadDefinition", methods[0].getName());
        assertEquals(RuntimeDefinitionInput.class, methods[0].getReturnType());
    }

    @Test
    void nullOfficialDefinitionIsRejected() {
        assertThrows(NullPointerException.class, () -> RuntimeDefinitionInput.from(null));
    }

    @Test
    void nullCollectionElementsAreRejectedAtReadBoundary() {
        ProcessDefinitionDetailDTO definitionWithNullNode = completeDefinition();
        definitionWithNullNode.setNodes(Collections.<ProcessNodeDTO>singletonList(null));
        ProcessDefinitionDetailDTO definitionWithNullEdge = completeDefinition();
        definitionWithNullEdge.setEdges(Collections.<ProcessEdgeDTO>singletonList(null));
        ProcessDefinitionDetailDTO definitionWithNullFormField = completeDefinition();
        definitionWithNullFormField.setFormFields(Collections.<ProcessFormFieldDTO>singletonList(null));
        ProcessDefinitionDetailDTO definitionWithNullAttachment = completeDefinition();
        definitionWithNullAttachment.setAttachmentTemplates(
                Collections.<ProcessAttachmentTemplateDTO>singletonList(null));

        assertThrows(NullPointerException.class,
                () -> RuntimeDefinitionInput.from(definitionWithNullNode));
        assertThrows(NullPointerException.class,
                () -> RuntimeDefinitionInput.from(definitionWithNullEdge));
        assertThrows(NullPointerException.class,
                () -> RuntimeDefinitionInput.from(definitionWithNullFormField));
        assertThrows(NullPointerException.class,
                () -> RuntimeDefinitionInput.from(definitionWithNullAttachment));
    }

    private static ProcessDefinitionDetailDTO completeDefinition() {
        ProcessDefinitionDetailDTO definition = new ProcessDefinitionDetailDTO();
        definition.setId("definition-1");
        definition.setProcessCode("generic-approval");
        definition.setVersion(3);
        definition.setDefinitionStatus(DefinitionStatusEnum.PUBLISHED);
        definition.setActivationStatus(ActivationStatusEnum.ACTIVE);
        definition.setNodes(new ArrayList<ProcessNodeDTO>(Arrays.asList(node())));
        definition.setEdges(new ArrayList<ProcessEdgeDTO>(Arrays.asList(edge())));
        definition.setFormFields(new ArrayList<ProcessFormFieldDTO>(Arrays.asList(formField())));
        definition.setAttachmentTemplates(
                new ArrayList<ProcessAttachmentTemplateDTO>(Arrays.asList(attachmentTemplate())));
        return definition;
    }

    private static ProcessNodeDTO node() {
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setId("node-1");
        node.setDefinitionId("definition-1");
        node.setNodeCode("review");
        node.setNodeName("Review");
        node.setNodeType(NodeTypeEnum.USER_TASK);
        node.setPairedGatewayCode("join");
        node.setApproverRuleType(ApproverRuleTypeEnum.ROLE);
        node.setApproverRuleConfig("{\"roleCodes\":[\"reviewer\"]}");
        node.setMultiInstanceMode(MultiInstanceModeEnum.COUNTERSIGN);
        node.setListenerConfig("{\"onComplete\":\"audit\"}");
        node.setTimeoutConfig("{\"hours\":24}");
        node.setReminderConfig("{\"minutes\":30}");
        node.setPositionX(120.5D);
        node.setPositionY(240.5D);
        node.setSortOrder(10);
        return node;
    }

    private static ProcessEdgeDTO edge() {
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setId("edge-1");
        edge.setDefinitionId("definition-1");
        edge.setEdgeCode("edge-review-end");
        edge.setSourceNodeCode("review");
        edge.setTargetNodeCode("end");
        edge.setConditionExpression("${approved == true}");
        edge.setDefaultEdge(Boolean.FALSE);
        edge.setSortOrder(20);
        return edge;
    }

    private static ProcessFormFieldDTO formField() {
        ProcessFormFieldDTO field = new ProcessFormFieldDTO();
        field.setId("field-1");
        field.setDefinitionId("definition-1");
        field.setFieldCode("amount");
        field.setFieldName("Amount");
        field.setFieldType("number");
        field.setControlType("number");
        field.setRequired(Boolean.TRUE);
        field.setValidationRule("{\"min\":0}");
        field.setDefaultValue("0");
        field.setSortOrder(30);
        return field;
    }

    private static ProcessAttachmentTemplateDTO attachmentTemplate() {
        ProcessAttachmentTemplateDTO attachment = new ProcessAttachmentTemplateDTO();
        attachment.setId("attachment-config-row-1");
        attachment.setAttachmentConfigId("config-group-1");
        attachment.setDefinitionId("definition-1");
        attachment.setConfigStatus(AttachmentConfigStatusEnum.ACTIVE);
        attachment.setActivatedAt(LocalDateTime.of(2026, 7, 17, 10, 30));
        attachment.setAttachmentTemplateId("template-version-2");
        attachment.setAttachmentCode("supporting-document");
        attachment.setTemplateVersion(2);
        attachment.setAttachmentName("Supporting document");
        attachment.setDescription("Generic supporting material");
        attachment.setAllowedExtensions(new ArrayList<String>(Arrays.asList("pdf", "png")));
        attachment.setMaxSizeBytes(10_485_760L);
        attachment.setTemplateStatus(AttachmentTemplateStatusEnum.ENABLED);
        attachment.setRequired(Boolean.TRUE);
        attachment.setMinCount(1);
        attachment.setMaxCount(3);
        attachment.setApplicableNodeCodes(new ArrayList<String>(Arrays.asList("review", "end")));
        attachment.setSortOrder(40);
        attachment.setCreatedBy("creator");
        attachment.setCreatedAt(LocalDateTime.of(2026, 7, 16, 9, 0));
        attachment.setUpdatedBy("updater");
        attachment.setUpdatedAt(LocalDateTime.of(2026, 7, 17, 9, 0));
        return attachment;
    }
}
