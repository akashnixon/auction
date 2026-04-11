package com.auction.notificationservice.services;

import com.auction.notificationservice.dto.BidMessageDTO;
import com.auction.notificationservice.dto.PublishEventRequest;
import com.auction.notificationservice.models.AuctionEndMessage;
import com.auction.notificationservice.models.NotificationEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {
    private static final int MAX_BUFFERED_EVENTS = 200;

    private final SimpMessagingTemplate messagingTemplate;
    private final Deque<NotificationEvent> events = new ConcurrentLinkedDeque<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendBidUpdate(BidMessageDTO bid) {
        publishInternal(
            buildEvent(
                "HIGHEST_BID_CHANGED",
                "ALL_REGISTERED",
                null,
                Map.of(
                    "auctionId", bid.getAuctionId(),
                    "bidderName", bid.getBidderName(),
                    "bidAmount", bid.getBidAmount()
                )
            ),
            "/topic/auction/" + bid.getAuctionId()
        );
    }

    public void sendAuctionEnd(AuctionEndMessage result) {
        publishInternal(
            buildEvent(
                "AUCTION_FINALIZED",
                "ALL_REGISTERED",
                null,
                Map.of(
                    "auctionId", result.getAuctionId(),
                    "winnerName", result.getWinnerName(),
                    "finalPrice", result.getFinalPrice()
                )
            ),
            "/topic/auction/" + result.getAuctionId()
        );
    }

    public NotificationEvent publishEvent(PublishEventRequest request) {
        NotificationEvent event = buildEvent(
            request.getType(),
            request.getAudience(),
            request.getTargetUserId(),
            request.getPayload()
        );

        String destination = "/topic/events";
        Object auctionId = request.getPayload() == null ? null : request.getPayload().get("auctionId");
        if (auctionId != null) {
            destination = "/topic/auction/" + auctionId;
        }

        publishInternal(event, destination);
        return event;
    }

    public List<NotificationEvent> listEvents(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_BUFFERED_EVENTS));
        List<NotificationEvent> snapshot = new ArrayList<>();
        int count = 0;
        for (NotificationEvent event : events) {
            snapshot.add(event);
            count++;
            if (count >= safeLimit) {
                break;
            }
        }
        return snapshot;
    }

    public int bufferedEventCount() {
        return events.size();
    }

    public int connectedClients() {
        return emitters.size();
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        return emitter;
    }

    private NotificationEvent buildEvent(String type, String audience, String targetUserId, Map<String, Object> payload) {
        NotificationEvent event = new NotificationEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setType(type);
        event.setAudience(audience);
        event.setTargetUserId(targetUserId);
        event.setPayload(payload);
        event.setCreatedAt(Instant.now().toString());
        return event;
    }

    private void publishInternal(NotificationEvent event, String destination) {
        events.addFirst(event);
        while (events.size() > MAX_BUFFERED_EVENTS) {
            events.pollLast();
        }

        messagingTemplate.convertAndSend(destination, event);
        if (!"/topic/events".equals(destination)) {
            messagingTemplate.convertAndSend("/topic/events", event);
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event.getType()).data(event));
            } catch (IOException ex) {
                emitter.complete();
                emitters.remove(emitter);
            }
        }
    }
}
