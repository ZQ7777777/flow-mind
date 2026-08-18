package com.flowmind.business.message;

import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.dto.ProcessNoticeDTO;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import com.flowmind.platform.api.spi.MessagePublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BusinessWorkflowCallbackHandlerTest {

    @Test
    void convertsNoticeEventToProcessNoticeMessage() {
        MessagePublisher publisher = mock(MessagePublisher.class);
        BusinessWorkflowCallbackHandler handler = new BusinessWorkflowCallbackHandler(publisher);
        ProcessNoticeDTO notice = new ProcessNoticeDTO();
        notice.setNodeCode("0199-notify-starter");
        notice.setNodeName("知会经办");
        notice.setInstanceTitle("仓单质押申请-001");
        notice.setTitle("流程知会");
        notice.setContent("已办理完成");
        notice.setTargetUserIds(Collections.singletonList("starter-1"));
        WorkflowEvent event = new WorkflowEvent();
        event.setEventId("op-1:NOTICE_CREATED:0199-notify-starter");
        event.setEventType(WorkflowEventTypeEnum.NOTICE_CREATED);
        event.setInstanceId("instance-1");
        event.setProcessCode("0199");
        event.setCreatedNotices(Collections.singletonList(notice));
        event.setOccurredAt(LocalDateTime.of(2026, 8, 18, 10, 0));

        handler.handle(event);

        ArgumentCaptor<ProcessMessage> captor = ArgumentCaptor.forClass(ProcessMessage.class);
        verify(publisher).publish(captor.capture());
        ProcessMessage message = captor.getValue();
        assertEquals(event.getEventId(), message.getMessageId());
        assertEquals("PROCESS_NOTICE", message.getMessageType());
        assertEquals(Collections.singletonList("starter-1"), message.getTargetUserIds());
        assertEquals("instance-1", message.getPayload().get("instanceId"));
        assertEquals("仓单质押申请-001", message.getPayload().get("instanceTitle"));
    }

    @Test
    void ignoresNonNoticeCallbacks() {
        MessagePublisher publisher = mock(MessagePublisher.class);
        BusinessWorkflowCallbackHandler handler = new BusinessWorkflowCallbackHandler(publisher);
        WorkflowEvent event = new WorkflowEvent();
        event.setEventType(WorkflowEventTypeEnum.PROCESS_COMPLETED);

        handler.handle(event);

        verify(publisher, never()).publish(org.mockito.ArgumentMatchers.any(ProcessMessage.class));
    }
}
