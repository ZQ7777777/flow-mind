package com.flowmind.business.message;

import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.dto.ProcessNoticeDTO;
import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import com.flowmind.platform.api.spi.MessagePublisher;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts platform notice callbacks into idempotent Business Base inbox messages. */
@Component
public class BusinessWorkflowCallbackHandler implements WorkflowCallbackHandler {

    private final MessagePublisher messagePublisher;

    public BusinessWorkflowCallbackHandler(MessagePublisher messagePublisher) {
        this.messagePublisher = messagePublisher;
    }

    @Override
    public void handle(WorkflowEvent event) {
        if (event == null || !WorkflowEventTypeEnum.NOTICE_CREATED.equals(event.getEventType())) {
            return;
        }
        List<ProcessNoticeDTO> notices = event.getCreatedNotices() == null
                ? Collections.<ProcessNoticeDTO>emptyList() : event.getCreatedNotices();
        for (int index = 0; index < notices.size(); index++) {
            ProcessNoticeDTO notice = notices.get(index);
            if (notice == null) {
                continue;
            }
            ProcessMessage message = new ProcessMessage();
            message.setMessageId(notices.size() == 1 ? event.getEventId() : event.getEventId() + ":" + index);
            message.setMessageType("PROCESS_NOTICE");
            message.setTitle(notice.getTitle());
            message.setContent(notice.getContent());
            message.setTargetUserIds(notice.getTargetUserIds());
            message.setPayload(payload(event, notice));
            message.setCreatedAt(event.getOccurredAt());
            messagePublisher.publish(message);
        }
    }

    private Map<String, Object> payload(WorkflowEvent event, ProcessNoticeDTO notice) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("instanceId", event.getInstanceId());
        payload.put("instanceTitle", notice.getInstanceTitle());
        payload.put("processCode", event.getProcessCode());
        payload.put("noticeNodeCode", notice.getNodeCode());
        payload.put("noticeNodeName", notice.getNodeName());
        return payload;
    }
}
