package com.flowmind.platform.api.request;

import com.flowmind.platform.api.entity.request.OperationRequest;
import com.flowmind.platform.api.entity.request.TaskOperationRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationRequestContractTest {

    @Test
    void operationRequestExposesOperationIdJavaBeanProperty() {
        OperationRequest request = new OperationRequest();

        request.setOperationId("operation-001");

        assertEquals("operation-001", request.getOperationId());
    }

    @Test
    void taskOperationRequestExtendsOperationRequestAndExposesTaskProperties() {
        TaskOperationRequest request = new TaskOperationRequest();

        request.setOperationId("operation-002");
        request.setTaskId("task-001");
        request.setExpectedTaskVersion(3L);
        request.setOperatorUserId("user-001");
        request.setComment("approve");

        assertTrue(OperationRequest.class.isAssignableFrom(TaskOperationRequest.class));
        assertEquals("operation-002", request.getOperationId());
        assertEquals("task-001", request.getTaskId());
        assertEquals(Long.valueOf(3L), request.getExpectedTaskVersion());
        assertEquals("user-001", request.getOperatorUserId());
        assertEquals("approve", request.getComment());
    }
}
