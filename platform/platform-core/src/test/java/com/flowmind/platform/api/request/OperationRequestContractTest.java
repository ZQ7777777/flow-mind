package com.flowmind.platform.api.request;

import com.flowmind.platform.api.enums.AttachmentAccessActionEnum;
import com.flowmind.platform.api.enums.AttachmentOwnerTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class  OperationRequestContractTest {

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

    @Test
    void attachmentAccessRequestUsesFrozenActionEnum() {
        AttachmentAccessRequest request = new AttachmentAccessRequest(
                "user-001",
                "Operator",
                AttachmentAccessActionEnum.DOWNLOAD,
                "instance-001",
                "task-001",
                "attachment-001",
                AttachmentOwnerTypeEnum.TASK);

        assertEquals(Arrays.asList(
                AttachmentAccessActionEnum.UPLOAD,
                AttachmentAccessActionEnum.VIEW,
                AttachmentAccessActionEnum.DOWNLOAD,
                AttachmentAccessActionEnum.DELETE), Arrays.asList(AttachmentAccessActionEnum.values()));
        assertEquals(AttachmentAccessActionEnum.DOWNLOAD, request.getAccessAction());
        request.setAccessAction(AttachmentAccessActionEnum.DELETE);
        assertEquals(AttachmentAccessActionEnum.DELETE, request.getAccessAction());
    }
}
