package com.auction.notificationservice.services;

import com.auction.notificationservice.dto.PublishEventRequest;
import com.auction.notificationservice.models.NotificationEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    public SseEmitter openStream(String userId) {
        // TODO: teammate implements connection registry and fan-out.
        return new SseEmitter(0L);
    }

    public NotificationEvent publish(PublishEventRequest request) {
        // TODO: teammate implements validation, persistence/history, and fan-out.
        throw new UnsupportedOperationException("publish not implemented");
    }

    public List<NotificationEvent> listEvents(int limit) {
        // TODO: teammate returns recent event history.
        return List.of();
    }

    public Map<String, Object> health() {
        return Map.of(
            "status", "Notification Service is healthy",
            "connectedClients", 0,
            "bufferedEvents", 0
        );
    }
}
