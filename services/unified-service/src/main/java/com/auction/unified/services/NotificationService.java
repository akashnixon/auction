package com.auction.unified.services;

import com.auction.unified.dto.PublishEventRequest;
import com.auction.unified.models.NotificationEvent;
import com.auction.unified.store.InMemoryStore;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Service
public class NotificationService {
    private static final int MAX_HISTORY = 500;
    private final InMemoryStore store;

    public NotificationService(InMemoryStore store) {
        this.store = store;
    }

    public SseEmitter stream(String userId) {
        String clientId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L);
        store.sseClients.put(clientId, emitter);
        store.sseClientUserIds.put(clientId, userId);

        emitter.onCompletion(() -> {
            store.sseClients.remove(clientId);
            store.sseClientUserIds.remove(clientId);
        });
        emitter.onTimeout(() -> {
            store.sseClients.remove(clientId);
            store.sseClientUserIds.remove(clientId);
        });
        emitter.onError((ex) -> {
            store.sseClients.remove(clientId);
            store.sseClientUserIds.remove(clientId);
        });

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("status", "connected")));
        } catch (IOException ignored) {
        }
        return emitter;
    }

    public NotificationEvent publish(PublishEventRequest request) {
        if (request.getType() == null || request.getType().isBlank()) {
            throw new IllegalArgumentException("type is required");
        }

        NotificationEvent event = new NotificationEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(request.getType());
        event.setAudience(request.getAudience() == null ? "ALL_REGISTERED" : request.getAudience());
        event.setTargetUserId(request.getTargetUserId());
        event.setPayload(request.getPayload() == null ? Map.of() : request.getPayload());
        event.setCreatedAt(Instant.now().toString());

        synchronized (store.eventHistory) {
            store.eventHistory.add(event);
            while (store.eventHistory.size() > MAX_HISTORY) {
                store.eventHistory.remove(0);
            }
        }

        fanout(event);
        return event;
    }

    public Map<String, Object> listEvents(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        List<NotificationEvent> snapshot;
        synchronized (store.eventHistory) {
            int from = Math.max(store.eventHistory.size() - safeLimit, 0);
            snapshot = new ArrayList<>(store.eventHistory.subList(from, store.eventHistory.size()));
        }
        return Map.of("total", snapshot.size(), "events", snapshot);
    }

    public Map<String, Object> health() {
        return Map.of(
            "status", "Notification Service is healthy",
            "connectedClients", store.sseClients.size(),
            "bufferedEvents", store.eventHistory.size()
        );
    }

    private void fanout(NotificationEvent event) {
        for (Map.Entry<String, SseEmitter> entry : store.sseClients.entrySet()) {
            String clientId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            String clientUserId = store.sseClientUserIds.get(clientId);

            if (!shouldReceive(event, clientUserId)) {
                continue;
            }

            try {
                emitter.send(SseEmitter.event().id(event.getEventId()).name(event.getType()).data(event));
            } catch (IOException ex) {
                store.sseClients.remove(clientId);
                store.sseClientUserIds.remove(clientId);
            }
        }
    }

    private boolean shouldReceive(NotificationEvent event, String clientUserId) {
        if ("ALL_REGISTERED".equals(event.getAudience())) {
            return true;
        }
        if ("SINGLE_USER".equals(event.getAudience())) {
            return clientUserId != null && clientUserId.equals(event.getTargetUserId());
        }
        return false;
    }
}
