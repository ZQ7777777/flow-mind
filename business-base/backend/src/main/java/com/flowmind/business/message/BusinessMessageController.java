package com.flowmind.business.message;

import com.flowmind.business.security.CurrentBusinessUserProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/messages")
public class BusinessMessageController {

    private final BusinessMessageService service;
    private final CurrentBusinessUserProvider currentUserProvider;
    private final UserMessageSseHub sseHub;

    public BusinessMessageController(BusinessMessageService service,
                                     CurrentBusinessUserProvider currentUserProvider,
                                     UserMessageSseHub sseHub) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.sseHub = sseHub;
    }

    @GetMapping
    public BusinessMessagePageResponse query(BusinessMessageQuery query) {
        return service.query(query);
    }

    @GetMapping("/unread-count")
    public BusinessUnreadCountResponse unreadCount() {
        return service.unreadCount();
    }

    @PostMapping("/{messageId}/read")
    public void markRead(@PathVariable String messageId) {
        service.markRead(messageId);
    }

    @PostMapping("/read-all")
    public void markAllRead() {
        service.markAllRead();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseHub.connect(currentUserProvider.currentUser().getUserId());
    }
}