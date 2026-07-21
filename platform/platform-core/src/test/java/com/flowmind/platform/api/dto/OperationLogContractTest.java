package com.flowmind.platform.api.dto;

import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.DefinitionActionTypeEnum;
import com.flowmind.platform.api.enums.OperationStatusEnum;
import com.flowmind.platform.api.enums.OperationTargetTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationLogContractTest {

    @Test
    void operationRecordExpressesIdempotencyState() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 15, 10, 0);
        LocalDateTime processingExpiresAt = LocalDateTime.of(2026, 7, 15, 10, 5);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 16, 10, 0);
        OperationRecordDTO record = new OperationRecordDTO();

        record.setOperationRecordId("record-001");
        record.setOperationId("operation-001");
        record.setInstanceId("instance-001");
        record.setTaskId("task-001");
        record.setActionType(ActionTypeEnum.APPROVE);
        record.setOperatorId("user-001");
        record.setRequestHash("hash-001");
        record.setOperationStatus(OperationStatusEnum.SUCCESS);
        record.setResultJson("{\"operationId\":\"operation-001\",\"replayed\":false}");
        record.setErrorCode("FLOW_TASK_CONCURRENT_MODIFIED");
        record.setProcessingExpiresAt(processingExpiresAt);
        record.setExpiresAt(expiresAt);
        record.setCreatedAt(createdAt);
        record.setUpdatedAt(processingExpiresAt);

        assertEquals("record-001", record.getOperationRecordId());
        assertEquals("operation-001", record.getOperationId());
        assertEquals("instance-001", record.getInstanceId());
        assertEquals("task-001", record.getTaskId());
        assertEquals("APPROVE", record.getActionType());
        assertEquals("user-001", record.getOperatorId());
        assertEquals("hash-001", record.getRequestHash());
        assertEquals(OperationStatusEnum.SUCCESS, record.getOperationStatus());
        assertEquals("{\"operationId\":\"operation-001\",\"replayed\":false}", record.getResultJson());
        assertEquals("FLOW_TASK_CONCURRENT_MODIFIED", record.getErrorCode());
        assertEquals(processingExpiresAt, record.getProcessingExpiresAt());
        assertEquals(expiresAt, record.getExpiresAt());
        assertEquals(createdAt, record.getCreatedAt());
        assertEquals(processingExpiresAt, record.getUpdatedAt());
    }

    @Test
    void operationRecordSupportsDefinitionActionNamespace() {
        OperationRecordDTO record = new OperationRecordDTO();

        record.setActionType(DefinitionActionTypeEnum.SAVE_GRAPH);

        assertEquals("DEFINITION_SAVE_GRAPH", record.getActionType());
        assertEquals("DEFINITION_DELETE", DefinitionActionTypeEnum.DELETE.getOperationActionType());
    }

    @Test
    void auditLogSupportsRuntimeActionType() {
        AuditLogDTO auditLog = new AuditLogDTO();

        auditLog.setTargetType(OperationTargetTypeEnum.TASK);
        auditLog.setTargetId("task-001");
        auditLog.setActionType(ActionTypeEnum.FORCE_COMPLETE);

        assertEquals(OperationTargetTypeEnum.TASK, auditLog.getTargetType());
        assertEquals("task-001", auditLog.getTargetId());
        assertEquals("FORCE_COMPLETE", auditLog.getActionType());
    }

    @Test
    void auditLogSupportsDefinitionActionNamespace() {
        AuditLogDTO auditLog = new AuditLogDTO();

        auditLog.setTargetType(OperationTargetTypeEnum.DEFINITION);
        auditLog.setTargetId("definition-001");
        auditLog.setActionType(DefinitionActionTypeEnum.DELETE);

        assertEquals(OperationTargetTypeEnum.DEFINITION, auditLog.getTargetType());
        assertEquals("definition-001", auditLog.getTargetId());
        assertEquals("DEFINITION_DELETE", auditLog.getActionType());
    }

    @Test
    void logContractsUseStringStorageWithEnumAdapters() throws NoSuchFieldException {
        OperationRecordDTO operationRecord = new OperationRecordDTO();
        AuditLogDTO auditLog = new AuditLogDTO();

        operationRecord.setActionType("DEFINITION_PUBLISH");
        auditLog.setActionType("DEFINITION_ACTIVATE");

        assertEquals(String.class, OperationRecordDTO.class.getDeclaredField("actionType").getType());
        assertEquals(String.class, AuditLogDTO.class.getDeclaredField("actionType").getType());
        assertEquals("DEFINITION_PUBLISH", operationRecord.getActionType());
        assertEquals("DEFINITION_ACTIVATE", auditLog.getActionType());
    }
}
