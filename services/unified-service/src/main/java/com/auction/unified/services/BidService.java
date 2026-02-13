package com.auction.unified.services;

import com.auction.unified.dto.PlaceBidRequest;
import com.auction.unified.dto.PublishEventRequest;
import com.auction.unified.models.AuctionRecord;
import com.auction.unified.models.AuctionStatus;
import com.auction.unified.models.BidRecord;
import com.auction.unified.store.InMemoryStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class BidService {
    private final InMemoryStore store;
    private final UserService userService;
    private final NotificationService notificationService;

    public BidService(InMemoryStore store, UserService userService, NotificationService notificationService) {
        this.store = store;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    public synchronized Map<String, Object> placeBid(PlaceBidRequest request) {
        if (request.getAuctionId() == null || request.getAuctionId().isBlank()
            || request.getBidderId() == null || request.getBidderId().isBlank()
            || request.getAmount() == null) {
            throw new IllegalArgumentException("auctionId, bidderId, and amount are required");
        }
        if (request.getAmount() <= 0) {
            throw new IllegalArgumentException("amount must be a positive number");
        }
        if (!userService.isActiveUser(request.getBidderId())) {
            throw new IllegalArgumentException("Only active registered users can bid");
        }

        AuctionRecord auction = store.auctions.get(request.getAuctionId());
        if (auction == null) {
            throw new NoSuchElementException("Auction not found");
        }
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new IllegalStateException("Auction is not active");
        }
        if (Instant.now().isAfter(auction.getEndTime())) {
            throw new IllegalStateException("Auction cycle has already ended");
        }

        String idem = request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()
            ? UUID.randomUUID().toString()
            : request.getIdempotencyKey().trim();
        String idemKey = request.getAuctionId() + ":" + request.getBidderId() + ":" + idem;
        if (store.processedIdempotency.containsKey(idemKey)) {
            return store.processedIdempotency.get(idemKey);
        }

        BidRecord bid = new BidRecord();
        bid.setBidId(UUID.randomUUID().toString());
        bid.setAuctionId(request.getAuctionId());
        bid.setCycleNumber(auction.getCycleNumber());
        bid.setBidderId(request.getBidderId());
        bid.setAmount(request.getAmount());
        bid.setReceivedAt(Instant.now().toString());
        bid.setIdempotencyKey(idem);

        store.bidsByAuction.computeIfAbsent(request.getAuctionId(), key -> new ArrayList<>()).add(bid);

        String cycleKey = cycleKey(request.getAuctionId(), auction.getCycleNumber());
        BidRecord previousHighest = store.highestByAuctionCycle.get(cycleKey);
        boolean changed = updateHighestIfNeeded(cycleKey, bid);
        BidRecord currentHighest = store.highestByAuctionCycle.get(cycleKey);

        if (changed && currentHighest != null) {
            if (previousHighest != null && !previousHighest.getBidderId().equals(currentHighest.getBidderId())) {
                userService.updateBidderStatus(previousHighest.getBidderId(), statusRequest(false, request.getAuctionId()));
            }
            userService.updateBidderStatus(currentHighest.getBidderId(), statusRequest(true, request.getAuctionId()));

            PublishEventRequest evt = new PublishEventRequest();
            evt.setType("HIGHEST_BID_CHANGED");
            evt.setAudience("ALL_REGISTERED");
            evt.setPayload(Map.of(
                "auctionId", bid.getAuctionId(),
                "cycleNumber", bid.getCycleNumber(),
                "bidderId", currentHighest.getBidderId(),
                "bidId", currentHighest.getBidId(),
                "amount", currentHighest.getAmount(),
                "receivedAt", currentHighest.getReceivedAt()
            ));
            notificationService.publish(evt);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("bidId", bid.getBidId());
        response.put("auctionId", bid.getAuctionId());
        response.put("cycleNumber", bid.getCycleNumber());
        response.put("bidderId", bid.getBidderId());
        response.put("amount", bid.getAmount());
        response.put("receivedAt", bid.getReceivedAt());
        response.put("isHighestBid", currentHighest != null && currentHighest.getBidId().equals(bid.getBidId()));
        response.put("idempotencyKey", idem);

        store.processedIdempotency.put(idemKey, response);
        return response;
    }

    public List<BidRecord> bidsForAuction(String auctionId) {
        List<BidRecord> bids = new ArrayList<>(store.bidsByAuction.getOrDefault(auctionId, List.of()));
        bids.sort(Comparator.comparing(BidRecord::getReceivedAt));
        return bids;
    }

    public Map<String, Object> activeHighestForUser(String userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (BidRecord highest : store.highestByAuctionCycle.values()) {
            if (!highest.getBidderId().equals(userId)) {
                continue;
            }
            AuctionRecord auction = store.auctions.get(highest.getAuctionId());
            if (auction != null && auction.getStatus() == AuctionStatus.ACTIVE) {
                result.add(Map.of(
                    "auctionId", highest.getAuctionId(),
                    "cycleNumber", highest.getCycleNumber(),
                    "bidId", highest.getBidId(),
                    "amount", highest.getAmount(),
                    "receivedAt", highest.getReceivedAt()
                ));
            }
        }
        return Map.of("userId", userId, "activeHighestBids", result);
    }

    public Map<String, Object> winnerForCycle(String auctionId, int cycleNumber) {
        BidRecord winner = store.highestByAuctionCycle.get(cycleKey(auctionId, cycleNumber));
        if (winner == null) {
            return Map.of();
        }
        return Map.of(
            "bidId", winner.getBidId(),
            "bidderId", winner.getBidderId(),
            "amount", String.valueOf(winner.getAmount()),
            "receivedAt", winner.getReceivedAt()
        );
    }

    public Map<String, Object> closeCycle(String auctionId, int cycleNumber) {
        BidRecord highest = store.highestByAuctionCycle.remove(cycleKey(auctionId, cycleNumber));
        if (highest != null) {
            userService.updateBidderStatus(highest.getBidderId(), statusRequest(false, auctionId));
        }
        return Map.of("auctionId", auctionId, "cycleNumber", cycleNumber, "closed", true);
    }

    private boolean updateHighestIfNeeded(String cycleKey, BidRecord candidate) {
        BidRecord current = store.highestByAuctionCycle.get(cycleKey);
        if (current == null) {
            store.highestByAuctionCycle.put(cycleKey, candidate);
            return true;
        }

        int compare = compareBids(candidate, current);
        if (compare < 0) {
            store.highestByAuctionCycle.put(cycleKey, candidate);
            return true;
        }
        return false;
    }

    private int compareBids(BidRecord a, BidRecord b) {
        if (a.getAmount() != b.getAmount()) {
            return Double.compare(b.getAmount(), a.getAmount());
        }
        int time = Instant.parse(a.getReceivedAt()).compareTo(Instant.parse(b.getReceivedAt()));
        if (time != 0) {
            return time;
        }
        return a.getBidId().compareTo(b.getBidId());
    }

    private String cycleKey(String auctionId, int cycleNumber) {
        return auctionId + ":" + cycleNumber;
    }

    private com.auction.unified.dto.UpdateBidderStatusRequest statusRequest(boolean value, String auctionId) {
        com.auction.unified.dto.UpdateBidderStatusRequest req = new com.auction.unified.dto.UpdateBidderStatusRequest();
        req.setIsHighestBidder(value);
        req.setAuctionId(auctionId);
        return req;
    }
}
