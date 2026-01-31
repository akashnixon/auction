package com.auction.auctionservice.schedulers;

import com.auction.auctionservice.services.AuctionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AuctionScheduler {

    private final AuctionService auctionService;

    public AuctionScheduler(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @Scheduled(fixedRate = 1000)
    public void checkAuctions() {
        // Placeholder:
        // 1. Find expired auctions
        // 2. Ask Bid Service for winning bid
        // 3. Finalize auction
    }
}
