package com.flowmind.platform.mock;

import com.flowmind.platform.api.dto.ProcessMessage;
import com.flowmind.platform.api.spi.MessagePublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 仅供本地验收和测试断言使用的记录型消息发布器。 */
public class RecordingMessagePublisher implements MessagePublisher {

    private final List<ProcessMessage> messages = Collections.synchronizedList(new ArrayList<ProcessMessage>());

    @Override
    public void publish(ProcessMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(LocalDateTime.now());
        }
        messages.add(message);
    }

    public List<ProcessMessage> getMessages() {
        synchronized (messages) {
            return Collections.unmodifiableList(new ArrayList<ProcessMessage>(messages));
        }
    }
}

