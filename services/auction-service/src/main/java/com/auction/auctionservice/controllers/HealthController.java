package com.auction.auctionservice.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${auction.duration-seconds:30}")
    private long auctionDurationSeconds;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "Auction Service is healthy",
            "time", Instant.now().toString(),
            "auctionDurationSeconds", auctionDurationSeconds
        ));
    }
}
