package com.auction.bidservice.services;

import com.auction.bidservice.dto.NotificationEventRequest;
import com.auction.bidservice.dto.PlaceBidRequest;
import com.auction.bidservice.models.Bid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BidService {

    private final RestTemplate restTemplate;

    private final Map<String, List<Bid>> bidsByAuction = new ConcurrentHashMap<>();
    private final Map<String, Bid> highestByAuctionCycle = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> processedIdempotency = new ConcurrentHashMap<>();

    @Value("${services.user.base-url:http://localhost:3001}")
    private String userServiceBaseUrl;

    @Value("${services.auction.base-url:http://localhost:3003}")
    private String auctionServiceBaseUrl;

    @Value("${services.notification.base-url:http://localhost:3005}")
    private String notificationServiceBaseUrl;

    public BidService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public synchronized Map<String, Object> placeBid(PlaceBidRequest request) {
        validateBidRequest(request);

        String auctionId = request.getAuctionId().trim();
        String bidderId = request.getBidderId().trim();
        double amount = request.getAmount();
        String idempotencyKey = request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()
            ? UUID.randomUUID().toString()
            : request.getIdempotencyKey().trim();

        String idemStoreKey = auctionId + ":" + bidderId + ":" + idempotencyKey;
        if (processedIdempotency.containsKey(idemStoreKey)) {
            return processedIdempotency.get(idemStoreKey);
        }

        Map<?, ?> user = fetchUser(bidderId);
        if (!Boolean.TRUE.equals(user.get("isActive"))) {
            throw new IllegalArgumentException("Only active registered users can bid");
        }

        Map<?, ?> auctionState = fetchAuctionState(auctionId);
        if (!"ACTIVE".equals(String.valueOf(auctionState.get("status")))) {
            throw new IllegalStateException("Auction is not active");
        }

        Instant endTime = Instant.parse(String.valueOf(auctionState.get("endTime")));
        if (Instant.now().isAfter(endTime)) {
            throw new IllegalStateException("Auction cycle has already ended");
        }

        int cycleNumber = ((Number) auctionState.get("cycleNumber")).intValue();
        Bid bid = new Bid();
        bid.setBidId(UUID.randomUUID().toString());
        bid.setAuctionId(auctionId);
        bid.setCycleNumber(cycleNumber);
        bid.setBidderId(bidderId);
        bid.setAmount(amount);
        bid.setReceivedAt(Instant.now().toString());
        bid.setIdempotencyKey(idempotencyKey);

        bidsByAuction.computeIfAbsent(auctionId, key -> new ArrayList<>()).add(bid);

        Bid previousHighest = highestByAuctionCycle.get(cycleKey(auctionId, cycleNumber));
        boolean changed = updateHighestBidIfNeeded(bid);
        Bid currentHighest = highestByAuctionCycle.get(cycleKey(auctionId, cycleNumber));

        if (changed && currentHighest != null) {
            if (previousHighest != null && !Objects.equals(previousHighest.getBidderId(), currentHighest.getBidderId())) {
                updateUserBidderStatus(previousHighest.getBidderId(), false, auctionId);
            }
            updateUserBidderStatus(currentHighest.getBidderId(), true, auctionId);

            publishEvent("HIGHEST_BID_CHANGED", Map.of(
                "auctionId", auctionId,
                "cycleNumber", cycleNumber,
                "bidderId", currentHighest.getBidderId(),
                "bidId", currentHighest.getBidId(),
                "amount", currentHighest.getAmount(),
                "receivedAt", currentHighest.getReceivedAt()
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("bidId", bid.getBidId());
        response.put("auctionId", bid.getAuctionId());
        response.put("cycleNumber", bid.getCycleNumber());
        response.put("bidderId", bid.getBidderId());
        response.put("amount", bid.getAmount());
        response.put("receivedAt", bid.getReceivedAt());
        response.put("isHighestBid", currentHighest != null && Objects.equals(currentHighest.getBidId(), bid.getBidId()));
        response.put("idempotencyKey", idempotencyKey);

        processedIdempotency.put(idemStoreKey, response);
        return response;
    }

    public List<Bid> listBidsForAuction(String auctionId) {
        List<Bid> bids = new ArrayList<>(bidsByAuction.getOrDefault(auctionId, List.of()));
        bids.sort(Comparator.comparing(Bid::getReceivedAt));
        return bids;
    }

    public Map<String, Object> listActiveHighestForUser(String userId) {
        List<Map<String, Object>> activeHighest = new ArrayList<>();

        for (Map.Entry<String, Bid> entry : highestByAuctionCycle.entrySet()) {
            Bid highest = entry.getValue();
            if (!Objects.equals(highest.getBidderId(), userId)) {
                continue;
            }

            String auctionId = highest.getAuctionId();
            try {
                Map<?, ?> auctionState = fetchAuctionState(auctionId);
                if ("ACTIVE".equals(String.valueOf(auctionState.get("status")))) {
                    activeHighest.add(Map.of(
                        "auctionId", auctionId,
                        "cycleNumber", ((Number) auctionState.get("cycleNumber")).intValue(),
                        "bidId", highest.getBidId(),
                        "amount", highest.getAmount(),
                        "receivedAt", highest.getReceivedAt()
                    ));
                }
            } catch (Exception ignored) {
            }
        }

        return Map.of("userId", userId, "activeHighestBids", activeHighest);
    }

    public Map<String, Object> getWinnerForCycle(String auctionId, int cycleNumber) {
        Bid winner = highestByAuctionCycle.get(cycleKey(auctionId, cycleNumber));
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
        Bid winner = highestByAuctionCycle.remove(cycleKey(auctionId, cycleNumber));
        if (winner != null) {
            updateUserBidderStatus(winner.getBidderId(), false, auctionId);
        }

        publishEvent("AUCTION_CYCLE_CLOSED", Map.of(
            "auctionId", auctionId,
            "cycleNumber", cycleNumber,
            "winnerBidId", winner != null ? winner.getBidId() : null,
            "winnerUserId", winner != null ? winner.getBidderId() : null
        ));

        return Map.of(
            "auctionId", auctionId,
            "cycleNumber", cycleNumber,
            "closed", true
        );
    }

    private void validateBidRequest(PlaceBidRequest request) {
        if (request == null || request.getAuctionId() == null || request.getAuctionId().isBlank()
            || request.getBidderId() == null || request.getBidderId().isBlank()
            || request.getAmount() == null) {
            throw new IllegalArgumentException("auctionId, bidderId, and amount are required");
        }
        if (request.getAmount() <= 0) {
            throw new IllegalArgumentException("amount must be a positive number");
        }
    }

    private String cycleKey(String auctionId, int cycleNumber) {
        return auctionId + ":" + cycleNumber;
    }

    private boolean updateHighestBidIfNeeded(Bid candidate) {
        String key = cycleKey(candidate.getAuctionId(), candidate.getCycleNumber());
        Bid current = highestByAuctionCycle.get(key);
        if (current == null) {
            highestByAuctionCycle.put(key, candidate);
            return true;
        }

        int comparison = compareBids(candidate, current);
        if (comparison < 0) {
            highestByAuctionCycle.put(key, candidate);
            return true;
        }

        return false;
    }

    private int compareBids(Bid a, Bid b) {
        if (a.getAmount() != b.getAmount()) {
            return Double.compare(b.getAmount(), a.getAmount());
        }

        int timeCompare = Instant.parse(a.getReceivedAt()).compareTo(Instant.parse(b.getReceivedAt()));
        if (timeCompare != 0) {
            return timeCompare;
        }

        return a.getBidId().compareTo(b.getBidId());
    }

    private Map<?, ?> fetchUser(String userId) {
        String url = userServiceBaseUrl + "/users/" + userId;
        return restTemplate.getForObject(url, Map.class);
    }

    private Map<?, ?> fetchAuctionState(String auctionId) {
        String url = auctionServiceBaseUrl + "/auctions/" + auctionId + "/state";
        return restTemplate.getForObject(url, Map.class);
    }

    private void updateUserBidderStatus(String userId, boolean isHighestBidder, String auctionId) {
        String url = userServiceBaseUrl + "/users/" + userId + "/update-bidder-status";
        Map<String, Object> payload = new HashMap<>();
        payload.put("isHighestBidder", isHighestBidder);
        payload.put("auctionId", auctionId);
        try {
            restTemplate.postForEntity(url, payload, Map.class);
        } catch (Exception ignored) {
        }
    }

    private void publishEvent(String type, Map<String, Object> payload) {
        String url = notificationServiceBaseUrl + "/events";
        NotificationEventRequest event = new NotificationEventRequest(type, "ALL_REGISTERED", null, payload);
        try {
            restTemplate.postForEntity(url, event, Map.class);
        } catch (Exception ignored) {
        }
    }
}
