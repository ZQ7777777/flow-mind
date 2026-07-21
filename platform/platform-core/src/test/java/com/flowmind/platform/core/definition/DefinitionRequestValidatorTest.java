package com.flowmind.platform.core.definition;

import com.flowmind.platform.api.dto.ProcessDefinitionQuery;
import com.flowmind.platform.api.dto.ProcessEdgeDTO;
import com.flowmind.platform.api.dto.ProcessNodeDTO;
import com.flowmind.platform.api.request.CopyProcessDefinitionRequest;
import com.flowmind.platform.api.request.CreateProcessDefinitionRequest;
import com.flowmind.platform.api.request.SaveProcessGraphRequest;
import com.flowmind.platform.core.validation.DefinitionRequestValidator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefinitionRequestValidatorTest {

    @Test
    void createDefinitionRequiresOperationOperatorAndBasicDefinitionFields() {
        CreateProcessDefinitionRequest request = validCreateRequest();
        assertDoesNotThrow(() -> DefinitionRequestValidator.validateCreate(request));

        request.setProcessCode(" ");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DefinitionRequestValidator.validateCreate(request));
        assertEquals("processCode must not be empty", exception.getMessage());
    }

    @Test
    void createDefinitionRejectsOverLengthFields() {
        CreateProcessDefinitionRequest request = validCreateRequest();
        request.setProcessName(repeat("a", DefinitionRequestValidator.MAX_PROCESS_NAME_LENGTH + 1));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DefinitionRequestValidator.validateCreate(request));

        assertEquals("processName length must be <= " + DefinitionRequestValidator.MAX_PROCESS_NAME_LENGTH,
                exception.getMessage());
    }

    @Test
    void copyDefinitionKeepsM1ProcessCodeDefaultSemantics() {
        CopyProcessDefinitionRequest request = new CopyProcessDefinitionRequest();
        request.setOperationId("operation-001");
        request.setOperatorUserId("operator-001");

        assertDoesNotThrow(() -> DefinitionRequestValidator.validateCopy(request));
    }

    @Test
    void saveGraphRequiresOperationOperatorNodesAndEdges() {
        SaveProcessGraphRequest request = validSaveGraphRequest();
        assertDoesNotThrow(() -> DefinitionRequestValidator.validateSaveGraph(request));

        request.setEdges(null);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> DefinitionRequestValidator.validateSaveGraph(request));
        assertEquals("edges must not be empty", exception.getMessage());
    }

    @Test
    void normalizesDefinitionQueryPaginationAndCapsPageSize() {
        ProcessDefinitionQuery query = new ProcessDefinitionQuery();
        query.setPageNo(Integer.valueOf(0));
        query.setPageSize(Integer.valueOf(1000));

        ProcessDefinitionQuery normalized = DefinitionRequestValidator.normalizeQuery(query);

        assertEquals(Integer.valueOf(DefinitionRequestValidator.DEFAULT_PAGE_NO), normalized.getPageNo());
        assertEquals(Integer.valueOf(DefinitionRequestValidator.MAX_PAGE_SIZE), normalized.getPageSize());
    }

    @Test
    void saveGraphRequestDoesNotExposeTaskLevelVersionField() {
        assertFalse(hasField(SaveProcessGraphRequest.class, "expectedTaskVersion"));
    }

    private static CreateProcessDefinitionRequest validCreateRequest() {
        CreateProcessDefinitionRequest request = new CreateProcessDefinitionRequest();
        request.setOperationId("operation-001");
        request.setOperatorUserId("operator-001");
        request.setProcessCode("deposit_apply");
        request.setProcessName("入金申请");
        request.setSystemCode("fund");
        return request;
    }

    private static SaveProcessGraphRequest validSaveGraphRequest() {
        SaveProcessGraphRequest request = new SaveProcessGraphRequest();
        request.setOperationId("operation-001");
        request.setOperatorUserId("operator-001");
        ProcessNodeDTO node = new ProcessNodeDTO();
        node.setNodeCode("start");
        ProcessEdgeDTO edge = new ProcessEdgeDTO();
        edge.setEdgeCode("edge-start-end");
        request.setNodes(Arrays.asList(node));
        request.setEdges(Arrays.asList(edge));
        return request;
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static boolean hasField(Class<?> type, String fieldName) {
        for (Field field : type.getDeclaredFields()) {
            if (fieldName.equals(field.getName())) {
                return true;
            }
        }
        return false;
    }
}
