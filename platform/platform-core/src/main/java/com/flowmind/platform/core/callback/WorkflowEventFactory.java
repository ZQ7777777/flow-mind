package com.flowmind.platform.core.callback;

import com.flowmind.platform.api.dto.HistoryTaskDTO;
import com.flowmind.platform.api.dto.TaskDTO;
import com.flowmind.platform.api.dto.UserContext;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流回调事件工厂，集中生成稳定 eventId。
 */
@Component
public class WorkflowEventFactory {

    public String eventId(String operationId, WorkflowEventTypeEnum eventType, int sequence) {
        if (operationId == null || operationId.trim().isEmpty()) {
            throw new IllegalArgumentException("operationId must not be empty");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        return operationId + ":" + eventType.name() + ":" + sequence;
    }

    public WorkflowEvent create(String operationId,
                                WorkflowEventTypeEnum eventType,
                                int sequence,
                                String processCode,
                                String instanceId,
                                ActionTypeEnum actionType,
                                UserContext operator,
                                List<HistoryTaskDTO> archivedTasks,
                                List<TaskDTO> createdTasks,
                                Map<String, Object> variables,
                                LocalDateTime occurredAt) {
        WorkflowEvent event = new WorkflowEvent();
        event.setEventId(eventId(operationId, eventType, sequence));
        event.setOperationId(operationId);
        event.setEventType(eventType);
        event.setProcessCode(processCode);
        event.setInstanceId(instanceId);
        event.setActionType(actionType);
        event.setOperator(operator);
        event.setArchivedTasks(archivedTasks);
        event.setCreatedTasks(createdTasks);
        event.setVariables(variables);
        event.setOccurredAt(occurredAt == null ? LocalDateTime.now() : occurredAt);
        return event;
    }
}
