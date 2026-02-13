package com.auction.unified.controllers;

import com.auction.unified.dto.PlaceBidRequest;
import com.auction.unified.models.BidRecord;
import com.auction.unified.services.BidService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class BidController {
    private final BidService bidService;

    public BidController(BidService bidService) {
        this.bidService = bidService;
    }

    @PostMapping("/bids")
    public ResponseEntity<Map<String, Object>> place(@RequestBody PlaceBidRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bidService.placeBid(request));
    }

    @GetMapping("/bids/auction/{auctionId}")
    public ResponseEntity<Map<String, Object>> listForAuction(@PathVariable String auctionId) {
        List<BidRecord> bids = bidService.bidsForAuction(auctionId);
        return ResponseEntity.ok(Map.of("auctionId", auctionId, "total", bids.size(), "bids", bids));
    }

    @GetMapping("/bids/user/{userId}/active-highest-bids")
    public ResponseEntity<Map<String, Object>> activeHighest(@PathVariable String userId) {
        return ResponseEntity.ok(bidService.activeHighestForUser(userId));
    }

    @GetMapping("/internal/auctions/{auctionId}/cycles/{cycleNumber}/winner")
    public ResponseEntity<Map<String, Object>> winner(@PathVariable String auctionId, @PathVariable int cycleNumber) {
        return ResponseEntity.ok(bidService.winnerForCycle(auctionId, cycleNumber));
    }

    @PostMapping("/internal/auctions/{auctionId}/cycles/{cycleNumber}/close")
    public ResponseEntity<Map<String, Object>> close(@PathVariable String auctionId, @PathVariable int cycleNumber) {
        return ResponseEntity.ok(bidService.closeCycle(auctionId, cycleNumber));
    }
}
