package com.auction.auctionservice.controllers;

import com.auction.auctionservice.dto.AuctionStateResponse;
import com.auction.auctionservice.dto.CreateAuctionRequest;
import com.auction.auctionservice.models.Auction;
import com.auction.auctionservice.models.AuctionStatus;
import com.auction.auctionservice.services.AuctionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auctions")
public class AuctionController {

    private final AuctionService auctionService;

    public AuctionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @PostMapping
    public ResponseEntity<?> createAuction(@RequestBody CreateAuctionRequest request) {
        try {
            Auction auction = auctionService.createAuction(request.getItemName(), request.getSellerId());
            return ResponseEntity.status(HttpStatus.CREATED).body(auction);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Auction>> listAllAuctions() {
        return ResponseEntity.ok(auctionService.listAllAuctions());
    }

    @GetMapping("/active")
    public ResponseEntity<List<Auction>> listActiveAuctions() {
        return ResponseEntity.ok(auctionService.listActiveAuctions());
    }

    @GetMapping("/{auctionId}")
    public ResponseEntity<?> getAuction(@PathVariable String auctionId) {
        try {
            UUID id = UUID.fromString(auctionId);
            Auction auction = auctionService.getAuction(id);
            if (auction == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Auction not found"));
            }
            return ResponseEntity.ok(auction);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid auctionId"));
        }
    }

    @GetMapping("/{auctionId}/state")
    public ResponseEntity<?> getAuctionState(@PathVariable String auctionId) {
        try {
            UUID id = UUID.fromString(auctionId);
            AuctionStateResponse state = auctionService.getAuctionState(id);
            if (state == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Auction not found"));
            }
            return ResponseEntity.ok(state);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid auctionId"));
        }
    }

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<Map<String, Object>> getActiveAuctionsForUser(@PathVariable String userId) {
        List<Auction> active = auctionService.listActiveAuctionsForSeller(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("activeAuctionsCount", active.size());
        response.put("activeAuctions", active);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "Auction Service is healthy");
        response.put("time", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        List<Auction> auctions = auctionService.listAllAuctions();
        long active = auctions.stream().filter(a -> a.getStatus() == AuctionStatus.ACTIVE).count();
        long ended = auctions.stream().filter(a -> a.getStatus() == AuctionStatus.ENDED).count();

        Map<String, Object> response = new HashMap<>();
        response.put("totalAuctions", auctions.size());
        response.put("activeAuctions", active);
        response.put("endedAuctions", ended);
        return ResponseEntity.ok(response);
    }
}
