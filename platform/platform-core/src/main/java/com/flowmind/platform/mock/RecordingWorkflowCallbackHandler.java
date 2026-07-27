package com.flowmind.platform.mock;

import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.spi.WorkflowCallbackHandler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 仅供本地验收和测试断言使用的记录型流程回调处理器。 */
public class RecordingWorkflowCallbackHandler implements WorkflowCallbackHandler {

    private final List<WorkflowEvent> events = Collections.synchronizedList(new ArrayList<WorkflowEvent>());

    @Override
    public void handle(WorkflowEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event is required");
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(LocalDateTime.now());
        }
        events.add(event);
    }

    public List<WorkflowEvent> getEvents() {
        synchronized (events) {
            return Collections.unmodifiableList(new ArrayList<WorkflowEvent>(events));
        }
    }
}

