package com.flowmind.platform.mock;

import com.flowmind.platform.api.dto.ProcessMessage;
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

class RecordingMessagePublisherTest {

    @Test
    void recordsMessagesAndFillsCreatedAt() {
        RecordingMessagePublisher publisher = new RecordingMessagePublisher();
        ProcessMessage message = message("message-1", null);

        publisher.publish(message);

        List<ProcessMessage> messages = publisher.getMessages();
        assertEquals(1, messages.size());
        assertEquals("message-1", messages.get(0).getMessageId());
        assertNotNull(messages.get(0).getCreatedAt());
    }

    @Test
    void preservesExistingCreatedAtAndPublishOrder() {
        RecordingMessagePublisher publisher = new RecordingMessagePublisher();
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 27, 11, 0);

        publisher.publish(message("message-1", createdAt));
        publisher.publish(message("message-2", createdAt.plusMinutes(1)));

        List<ProcessMessage> messages = publisher.getMessages();
        assertEquals(Arrays.asList("message-1", "message-2"),
                Arrays.asList(messages.get(0).getMessageId(), messages.get(1).getMessageId()));
        assertEquals(createdAt, messages.get(0).getCreatedAt());
    }

    @Test
    void messagesSnapshotCannotModifyInternalState() {
        RecordingMessagePublisher publisher = new RecordingMessagePublisher();
        publisher.publish(message("message-1", null));

        assertThrows(UnsupportedOperationException.class, () -> publisher.getMessages().clear());
        assertEquals(1, publisher.getMessages().size());
    }

    @Test
    void rejectsNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> new RecordingMessagePublisher().publish(null));
    }

    @Test
    void concurrentPublishesAreRecorded() throws Exception {
        final RecordingMessagePublisher publisher = new RecordingMessagePublisher();
        final int count = 16;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            final int index = i;
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        publisher.publish(message("message-" + index, null));
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertEquals(count, publisher.getMessages().size());
    }

    private static ProcessMessage message(String id, LocalDateTime createdAt) {
        ProcessMessage message = new ProcessMessage();
        message.setMessageId(id);
        message.setMessageType("TEST");
        message.setTitle("title");
        message.setContent("content");
        message.setTargetUserIds(Arrays.asList("user-1"));
        message.setCreatedAt(createdAt);
        return message;
    }
}

