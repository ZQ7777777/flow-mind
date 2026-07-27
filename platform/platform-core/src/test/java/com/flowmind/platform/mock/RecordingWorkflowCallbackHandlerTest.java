package com.flowmind.platform.mock;

import com.flowmind.platform.api.dto.WorkflowEvent;
import com.flowmind.platform.api.enums.ActionTypeEnum;
import com.flowmind.platform.api.enums.WorkflowEventTypeEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingWorkflowCallbackHandlerTest {

    @Test
    void recordsEventsAndFillsOccurredAt() {
        RecordingWorkflowCallbackHandler handler = new RecordingWorkflowCallbackHandler();
        WorkflowEvent event = event("event-1", null);

        handler.handle(event);

        List<WorkflowEvent> events = handler.getEvents();
        assertEquals(1, events.size());
        assertEquals("event-1", events.get(0).getEventId());
        assertNotNull(events.get(0).getOccurredAt());
    }

    @Test
    void preservesExistingOccurredAtAndHandleOrder() {
        RecordingWorkflowCallbackHandler handler = new RecordingWorkflowCallbackHandler();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 7, 27, 13, 30);

        handler.handle(event("event-1", occurredAt));
        handler.handle(event("event-2", occurredAt.plusMinutes(1)));

        List<WorkflowEvent> events = handler.getEvents();
        assertEquals(Arrays.asList("event-1", "event-2"),
                Arrays.asList(events.get(0).getEventId(), events.get(1).getEventId()));
        assertEquals(occurredAt, events.get(0).getOccurredAt());
    }

    @Test
    void eventsSnapshotCannotModifyInternalState() {
        RecordingWorkflowCallbackHandler handler = new RecordingWorkflowCallbackHandler();
        handler.handle(event("event-1", null));

        assertThrows(UnsupportedOperationException.class, () -> handler.getEvents().clear());
        assertEquals(1, handler.getEvents().size());
    }

    @Test
    void rejectsNullEvent() {
        assertThrows(IllegalArgumentException.class, () -> new RecordingWorkflowCallbackHandler().handle(null));
    }

    @Test
    void concurrentEventsAreRecorded() throws Exception {
        final RecordingWorkflowCallbackHandler handler = new RecordingWorkflowCallbackHandler();
        final int count = 16;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            final int index = i;
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        handler.handle(event("event-" + index, null));
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertEquals(count, handler.getEvents().size());
    }

    private static WorkflowEvent event(String id, LocalDateTime occurredAt) {
        WorkflowEvent event = new WorkflowEvent();
        event.setEventId(id);
        event.setOperationId("operation-" + id);
        event.setEventType(WorkflowEventTypeEnum.TASK_COMPLETED);
        event.setProcessCode("process");
        event.setInstanceId("instance");
        event.setActionType(ActionTypeEnum.APPROVE);
        event.setOccurredAt(occurredAt);
        return event;
    }
}

