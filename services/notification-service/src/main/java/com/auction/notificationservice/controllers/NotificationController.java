package com.auction.notificationservice.controllers;

import com.auction.notificationservice.dto.PublishEventRequest;
import com.auction.notificationservice.models.NotificationEvent;
import com.auction.notificationservice.services.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam(value = "userId", required = false) String userId) {
        return notificationService.openStream(userId);
    }

    @PostMapping("/events")
    public ResponseEntity<?> publish(@RequestBody PublishEventRequest request) {
        try {
            NotificationEvent event = notificationService.publish(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(event);
        } catch (UnsupportedOperationException ex) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/events")
    public ResponseEntity<?> listEvents(@RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        List<NotificationEvent> events = notificationService.listEvents(limit);
        return ResponseEntity.ok(Map.of("total", events.size(), "events", events));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(notificationService.health());
    }
}
