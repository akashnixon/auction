package com.auction.unified.controllers;

import com.auction.unified.services.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {
    private final NotificationService notificationService;

    public HealthController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "Unified Auction Service is healthy",
            "time", Instant.now().toString(),
            "notification", notificationService.health()
        ));
    }
}
