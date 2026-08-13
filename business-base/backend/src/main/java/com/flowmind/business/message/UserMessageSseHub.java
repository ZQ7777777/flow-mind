package com.flowmind.business.message;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory SSE connection hub; message rows remain the recovery source. */
@Component
public class UserMessageSseHub {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>();

    public SseEmitter connect(String userId) {
        final SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            userEmitters = new CopyOnWriteArrayList<SseEmitter>();
            emitters.put(userId, userEmitters);
        }
        userEmitters.add(emitter);
        final String finalUserId = userId;
        emitter.onCompletion(new Runnable() {
            @Override
            public void run() { remove(finalUserId, emitter); }
        });
        emitter.onTimeout(new Runnable() {
            @Override
            public void run() { remove(finalUserId, emitter); }
        });
        return emitter;
    }

    public void publish(String userId, BusinessUserMessageEntity message) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }
        List<SseEmitter> failed = new ArrayList<SseEmitter>();
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("message").data(message));
            } catch (IOException | IllegalStateException ex) {
                failed.add(emitter);
            }
        }
        userEmitters.removeAll(failed);
    }

    private void remove(String userId, SseEmitter emitter) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
        }
    }
}