package com.auction.unified.services;

import com.auction.unified.dto.CreateAuctionRequest;
import com.auction.unified.dto.PublishEventRequest;
import com.auction.unified.dto.UpdateSellerStatusRequest;
import com.auction.unified.models.AuctionRecord;
import com.auction.unified.models.AuctionStatus;
import com.auction.unified.store.InMemoryStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class AuctionService {
    private final InMemoryStore store;
    private final UserService userService;
    private final BidService bidService;
    private final NotificationService notificationService;

    public AuctionService(InMemoryStore store, UserService userService, BidService bidService, NotificationService notificationService) {
        this.store = store;
        this.userService = userService;
        this.bidService = bidService;
        this.notificationService = notificationService;
    }

    public AuctionRecord create(CreateAuctionRequest request) {
        if (request.getItemName() == null || request.getItemName().isBlank()) {
            throw new IllegalArgumentException("itemName is required");
        }
        if (request.getSellerId() == null || request.getSellerId().isBlank()) {
            throw new IllegalArgumentException("sellerId is required");
        }
        if (!userService.isActiveUser(request.getSellerId())) {
            throw new IllegalArgumentException("Seller must be an active registered user");
        }

        AuctionRecord auction = new AuctionRecord();
        auction.setAuctionId(UUID.randomUUID().toString());
        auction.setItemName(request.getItemName().trim());
        auction.setSellerId(request.getSellerId().trim());
        auction.setCycleNumber(1);
        auction.setStartTime(Instant.now());
        auction.setEndTime(auction.getStartTime().plusSeconds(300));
        auction.setStatus(AuctionStatus.ACTIVE);

        store.auctions.put(auction.getAuctionId(), auction);

        UpdateSellerStatusRequest sellerStatus = new UpdateSellerStatusRequest();
        sellerStatus.setIsSelling(true);
        sellerStatus.setAuctionId(auction.getAuctionId());
        userService.updateSellerStatus(auction.getSellerId(), sellerStatus);

        PublishEventRequest evt = new PublishEventRequest();
        evt.setType("AUCTION_ADVERTISED");
        evt.setAudience("ALL_REGISTERED");
        evt.setPayload(Map.of(
            "auctionId", auction.getAuctionId(),
            "itemName", auction.getItemName(),
            "sellerId", auction.getSellerId(),
            "cycleNumber", auction.getCycleNumber(),
            "startTime", auction.getStartTime().toString(),
            "endTime", auction.getEndTime().toString()
        ));
        notificationService.publish(evt);

        return auction;
    }

    public AuctionRecord get(String auctionId) {
        AuctionRecord auction = store.auctions.get(auctionId);
        if (auction == null) {
            throw new NoSuchElementException("Auction not found");
        }
        return auction;
    }

    public List<AuctionRecord> listAll() {
        List<AuctionRecord> list = new ArrayList<>(store.auctions.values());
        list.sort(Comparator.comparing(AuctionRecord::getStartTime).reversed());
        return list;
    }

    public List<AuctionRecord> listActive() {
        List<AuctionRecord> result = new ArrayList<>();
        for (AuctionRecord auction : store.auctions.values()) {
            if (auction.getStatus() == AuctionStatus.ACTIVE) {
                result.add(auction);
            }
        }
        return result;
    }

    public Map<String, Object> state(String auctionId) {
        AuctionRecord auction = get(auctionId);
        return Map.of(
            "auctionId", auction.getAuctionId(),
            "itemName", auction.getItemName(),
            "sellerId", auction.getSellerId(),
            "status", auction.getStatus().name(),
            "cycleNumber", auction.getCycleNumber(),
            "startTime", auction.getStartTime().toString(),
            "endTime", auction.getEndTime().toString()
        );
    }

    public Map<String, Object> activeForSeller(String userId) {
        List<AuctionRecord> result = new ArrayList<>();
        for (AuctionRecord auction : store.auctions.values()) {
            if (auction.getStatus() == AuctionStatus.ACTIVE && auction.getSellerId().equals(userId)) {
                result.add(auction);
            }
        }
        return Map.of("userId", userId, "activeAuctionsCount", result.size(), "activeAuctions", result);
    }

    @Scheduled(fixedRate = 1000)
    public void checkExpiredAuctions() {
        Instant now = Instant.now();
        for (AuctionRecord auction : store.auctions.values()) {
            if (auction.getStatus() != AuctionStatus.ACTIVE || auction.getEndTime().isAfter(now)) {
                continue;
            }

            if (!auction.acquireClosingLock()) {
                continue;
            }
            try {
                if (auction.getStatus() != AuctionStatus.ACTIVE || auction.getEndTime().isAfter(Instant.now())) {
                    continue;
                }

                Map<String, Object> winner = bidService.winnerForCycle(auction.getAuctionId(), auction.getCycleNumber());
                if (winner.isEmpty()) {
                    restartCycle(auction);
                } else {
                    finalizeWithWinner(auction, winner);
                }
            } finally {
                auction.releaseClosingLock();
            }
        }
    }

    private void restartCycle(AuctionRecord auction) {
        auction.setCycleNumber(auction.getCycleNumber() + 1);
        auction.setStartTime(Instant.now());
        auction.setEndTime(auction.getStartTime().plusSeconds(300));

        PublishEventRequest evt = new PublishEventRequest();
        evt.setType("AUCTION_RESTARTED");
        evt.setAudience("ALL_REGISTERED");
        evt.setPayload(Map.of(
            "auctionId", auction.getAuctionId(),
            "itemName", auction.getItemName(),
            "sellerId", auction.getSellerId(),
            "cycleNumber", auction.getCycleNumber(),
            "startTime", auction.getStartTime().toString(),
            "endTime", auction.getEndTime().toString()
        ));
        notificationService.publish(evt);
    }

    private void finalizeWithWinner(AuctionRecord auction, Map<String, Object> winner) {
        auction.setWinningBidId(String.valueOf(winner.get("bidId")));
        auction.setWinnerUserId(String.valueOf(winner.get("bidderId")));
        auction.setFinalizedAt(Instant.now());
        auction.setStatus(AuctionStatus.ENDED);

        UpdateSellerStatusRequest sellerStatus = new UpdateSellerStatusRequest();
        sellerStatus.setIsSelling(false);
        sellerStatus.setAuctionId(auction.getAuctionId());
        userService.updateSellerStatus(auction.getSellerId(), sellerStatus);

        bidService.closeCycle(auction.getAuctionId(), auction.getCycleNumber());

        Map<String, Object> payload = Map.of(
            "auctionId", auction.getAuctionId(),
            "itemName", auction.getItemName(),
            "sellerId", auction.getSellerId(),
            "winnerUserId", auction.getWinnerUserId(),
            "winningBidId", auction.getWinningBidId(),
            "winningAmount", String.valueOf(winner.get("amount")),
            "cycleNumber", auction.getCycleNumber(),
            "finalizedAt", auction.getFinalizedAt().toString()
        );

        PublishEventRequest evt = new PublishEventRequest();
        evt.setType("AUCTION_FINALIZED");
        evt.setAudience("ALL_REGISTERED");
        evt.setPayload(payload);
        notificationService.publish(evt);

        PublishEventRequest winnerEvt = new PublishEventRequest();
        winnerEvt.setType("AUCTION_WON");
        winnerEvt.setAudience("SINGLE_USER");
        winnerEvt.setTargetUserId(auction.getWinnerUserId());
        winnerEvt.setPayload(payload);
        notificationService.publish(winnerEvt);

        PublishEventRequest sellerEvt = new PublishEventRequest();
        sellerEvt.setType("AUCTION_SOLD");
        sellerEvt.setAudience("SINGLE_USER");
        sellerEvt.setTargetUserId(auction.getSellerId());
        sellerEvt.setPayload(payload);
        notificationService.publish(sellerEvt);
    }
}
