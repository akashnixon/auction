package com.auction.auctionservice.services;

import com.auction.auctionservice.models.Auction;
import com.auction.auctionservice.models.AuctionStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuctionService {

    // In-memory for now (replace with DB)
    private final Map<UUID, Auction> auctions = new ConcurrentHashMap<>();

    public Auction createAuction(String itemName, String sellerId) {
        Auction auction = new Auction(itemName, sellerId);
        auctions.put(auction.getAuctionId(), auction);
        return auction;
    }

    public Auction getAuction(UUID auctionId) {
        return auctions.get(auctionId);
    }

    /**
     * Finalizes auction safely (called by scheduler / leader)
     */
    public synchronized void finalizeAuction(UUID auctionId, String winningBidId) {
        Auction auction = auctions.get(auctionId);

        if (auction == null || auction.getStatus() != AuctionStatus.ACTIVE) {
            return; // idempotent
        }

        if (winningBidId == null) {
            // No bids → restart
            auction.setStartTime(Instant.now());
            auction.setEndTime(auction.getStartTime().plusSeconds(300));
            auction.setStatus(AuctionStatus.RESTARTED);
            return;
        }

        auction.setWinningBidId(winningBidId);
        auction.setStatus(AuctionStatus.ENDED);
    }
}
