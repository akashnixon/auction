package com.auction.unified.controllers;

import com.auction.unified.dto.CreateAuctionRequest;
import com.auction.unified.models.AuctionRecord;
import com.auction.unified.services.AuctionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auctions")
public class AuctionController {
    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @PostMapping
    public ResponseEntity<AuctionRecord> create(@RequestBody CreateAuctionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auctionService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<AuctionRecord>> listAll() {
        return ResponseEntity.ok(auctionService.listAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<AuctionRecord>> listActive() {
        return ResponseEntity.ok(auctionService.listActive());
    }

    @GetMapping("/{auctionId}")
    public ResponseEntity<AuctionRecord> get(@PathVariable String auctionId) {
        return ResponseEntity.ok(auctionService.get(auctionId));
    }

    @GetMapping("/{auctionId}/state")
    public ResponseEntity<Map<String, Object>> state(@PathVariable String auctionId) {
        return ResponseEntity.ok(auctionService.state(auctionId));
    }

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<Map<String, Object>> activeByUser(@PathVariable String userId) {
        return ResponseEntity.ok(auctionService.activeForSeller(userId));
    }
}
