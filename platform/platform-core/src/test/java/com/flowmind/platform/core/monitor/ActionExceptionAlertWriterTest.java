package com.flowmind.platform.core.monitor;

import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.enums.AlertSeverityEnum;
import com.flowmind.platform.api.enums.AlertTypeEnum;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.persistence.entity.ProcessAlertRecordEntity;
import com.flowmind.platform.persistence.repository.AlertRecordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionExceptionAlertWriterTest {

    @Test
    void publishesSanitizedAlertMessageAfterWritingActionException() {
        AlertRecordRepository alerts = mock(AlertRecordRepository.class);
        MessagePublisher publisher = mock(MessagePublisher.class);
        ActionExceptionAlertWriter writer = new ActionExceptionAlertWriter(alerts, publisher,
                Collections.singletonList("admin-1"));
        when(alerts.findOpenByTypeAndDetailValue(AlertTypeEnum.ACTION_EXCEPTION.name(), "dedupKey",
                "operation-1:JUMP")).thenReturn(null);
        when(alerts.insert(any(ProcessAlertRecordEntity.class))).thenReturn(Integer.valueOf(1));

        writer.write("operation-1", "JUMP", "instance-1", "task-1", "INVALID_ACTION",
                "stack trace with implementation details", "operator-1");

        ArgumentCaptor<ProcessMessage> messageCaptor = ArgumentCaptor.forClass(ProcessMessage.class);
        verify(publisher).publish(messageCaptor.capture());
        ProcessMessage message = messageCaptor.getValue();
        assertEquals("ALERT", message.getMessageType());
        assertEquals(Collections.singletonList("admin-1"), message.getTargetUserIds());
        assertEquals("instance-1", message.getPayload().get("instanceId"));
        assertEquals("task-1", message.getPayload().get("taskId"));
        assertEquals(AlertTypeEnum.ACTION_EXCEPTION.name(), message.getPayload().get("alertType"));
        assertEquals(AlertSeverityEnum.HIGH.name(), message.getPayload().get("severity"));
        assertFalse(message.getPayload().containsKey("errorSummary"));
        assertFalse(message.getPayload().containsKey("operatorId"));
    }
}