package com.auction.bidservice.controllers;

import com.auction.bidservice.dto.PlaceBidRequest;
import com.auction.bidservice.models.Bid;
import com.auction.bidservice.services.BidService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class BidController {

    private final BidService bidService;

    public BidController(BidService bidService) {
        this.bidService = bidService;
    }

    @PostMapping("/bids")
    public ResponseEntity<?> placeBid(@RequestBody PlaceBidRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(bidService.placeBid(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/bids/auction/{auctionId}")
    public ResponseEntity<Map<String, Object>> listByAuction(@PathVariable String auctionId) {
        List<Bid> bids = bidService.listBidsForAuction(auctionId);
        Map<String, Object> response = new HashMap<>();
        response.put("auctionId", auctionId);
        response.put("total", bids.size());
        response.put("bids", bids);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bids/user/{userId}/active-highest-bids")
    public ResponseEntity<Map<String, Object>> activeHighest(@PathVariable String userId) {
        return ResponseEntity.ok(bidService.listActiveHighestForUser(userId));
    }

    @GetMapping("/internal/auctions/{auctionId}/cycles/{cycleNumber}/winner")
    public ResponseEntity<Map<String, Object>> winner(@PathVariable String auctionId, @PathVariable int cycleNumber) {
        return ResponseEntity.ok(bidService.getWinnerForCycle(auctionId, cycleNumber));
    }

    @PostMapping("/internal/auctions/{auctionId}/cycles/{cycleNumber}/close")
    public ResponseEntity<Map<String, Object>> close(@PathVariable String auctionId, @PathVariable int cycleNumber) {
        return ResponseEntity.ok(bidService.closeCycle(auctionId, cycleNumber));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "Bid Service is healthy"));
    }
}
