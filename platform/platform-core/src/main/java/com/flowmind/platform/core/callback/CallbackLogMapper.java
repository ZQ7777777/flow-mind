package com.flowmind.platform.core.callback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.api.dto.CallbackLogDTO;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.CallbackStatusEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import com.flowmind.platform.persistence.entity.ProcessCallbackLogEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 回调日志实体、DTO 和事件载荷映射器。
 */
@Component
public class CallbackLogMapper {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ProcessCallbackLogEntity toPendingEntity(WorkflowEvent event) {
        LocalDateTime now = LocalDateTime.now();
        ProcessCallbackLogEntity entity = new ProcessCallbackLogEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setEventId(event.getEventId());
        entity.setInstanceId(event.getInstanceId());
        entity.setOperationId(event.getOperationId());
        entity.setEventType(event.getEventType().name());
        entity.setActionType(event.getActionType().name());
        entity.setPayloadJson(toPayloadJson(event));
        entity.setCallbackStatus(CallbackStatusEnum.PENDING.name());
        entity.setRetryCount(Integer.valueOf(0));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    public CallbackLogDTO toDTO(ProcessCallbackLogEntity entity) {
        CallbackLogDTO dto = new CallbackLogDTO();
        dto.setCallbackLogId(entity.getId());
        dto.setEventId(entity.getEventId());
        dto.setInstanceId(entity.getInstanceId());
        dto.setOperationId(entity.getOperationId());
        dto.setEventType(WorkflowEventTypeEnum.valueOf(entity.getEventType()));
        dto.setActionType(ActionTypeEnum.valueOf(entity.getActionType()));
        dto.setPayloadJson(entity.getPayloadJson());
        dto.setCallbackStatus(CallbackStatusEnum.valueOf(entity.getCallbackStatus()));
        dto.setRetryCount(entity.getRetryCount());
        dto.setLastError(entity.getLastError());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public String toPayloadJson(WorkflowEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("workflow event json write failed", ex);
        }
    }
}
