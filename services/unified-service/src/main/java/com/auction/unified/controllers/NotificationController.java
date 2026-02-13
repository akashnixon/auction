package com.auction.unified.controllers;

import com.auction.unified.dto.PublishEventRequest;
import com.auction.unified.models.NotificationEvent;
import com.auction.unified.services.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam(value = "userId", required = false) String userId) {
        return notificationService.stream(userId);
    }

    @PostMapping("/events")
    public ResponseEntity<NotificationEvent> publish(@RequestBody PublishEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notificationService.publish(request));
    }

    @GetMapping("/events")
    public ResponseEntity<Map<String, Object>> events(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(notificationService.listEvents(limit));
    }
}
