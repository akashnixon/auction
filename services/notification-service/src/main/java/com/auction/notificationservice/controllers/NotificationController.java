package com.auction.notificationservice.controllers;

import com.auction.notificationservice.dto.BidMessageDTO;
import com.auction.notificationservice.dto.PublishEventRequest;
import com.auction.notificationservice.models.AuctionEndMessage;
import com.auction.notificationservice.models.NotificationEvent;
import com.auction.notificationservice.services.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @MessageMapping("/bid")
    public void handleBid(BidMessageDTO bid) {
        notificationService.sendBidUpdate(bid);
    }

    @MessageMapping("/auctionEnd")
    public void handleAuctionEnd(AuctionEndMessage result) {
        notificationService.sendAuctionEnd(result);
    }

    @PostMapping("/events")
    public ResponseEntity<NotificationEvent> publishEvent(@RequestBody PublishEventRequest request) {
        NotificationEvent event = notificationService.publishEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @GetMapping("/events")
    public ResponseEntity<Map<String, Object>> listEvents(@RequestParam(defaultValue = "20") int limit) {
        List<NotificationEvent> events = notificationService.listEvents(limit);
        return ResponseEntity.ok(Map.of(
            "total", events.size(),
            "events", events
        ));
    }

    @GetMapping("/stream")
    public SseEmitter stream() {
        return notificationService.subscribe();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "Notification Service is healthy",
            "bufferedEvents", notificationService.bufferedEventCount(),
            "connectedClients", notificationService.connectedClients()
        ));
    }
}
