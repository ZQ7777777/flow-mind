package com.flowmind.platform.core.audit;

import com.flowmind.platform.core.runtime.RuntimeErrorCodes;
import com.flowmind.platform.core.runtime.RuntimeJsonCodec;
import com.flowmind.platform.core.runtime.RuntimeValidationException;
import com.flowmind.platform.persistence.entity.ProcessAuditLogEntity;
import com.flowmind.platform.persistence.repository.ProcessAuditLogRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/** Default audit writer backed by {@code process_audit_log}. */
@Component
public class DefaultAuditLogWriter implements AuditLogWriter {

    private final ProcessAuditLogRepository auditLogRepository;

    public DefaultAuditLogWriter(ProcessAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public ProcessAuditLogEntity append(AuditLogCommand command) {
        validate(command);
        ProcessAuditLogEntity entity = new ProcessAuditLogEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setInstanceId(command.getInstanceId());
        entity.setOperationId(command.getOperationId());
        entity.setTargetType(command.getTargetType().name());
        entity.setTargetId(command.getTargetId());
        entity.setActionType(command.getActionType());
        entity.setOperatorId(command.getOperatorId());
        entity.setDetailJson(RuntimeJsonCodec.toJson(command.getDetail()));
        entity.setCreatedAt(LocalDateTime.now());
        if (auditLogRepository.insert(entity) != 1) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION, "failed to append audit log");
        }
        return entity;
    }

    private void validate(AuditLogCommand command) {
        if (command == null || command.getTargetType() == null || isBlank(command.getTargetId())
                || isBlank(command.getActionType()) || isBlank(command.getOperatorId())) {
            throw new RuntimeValidationException(RuntimeErrorCodes.INVALID_ACTION,
                    "audit target, action and operator are required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
