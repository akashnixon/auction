package com.auction.auctionservice.services;

import com.auction.auctionservice.dto.AuctionStateResponse;
import com.auction.auctionservice.dto.BidWinnerResponse;
import com.auction.auctionservice.dto.NotificationEventRequest;
import com.auction.auctionservice.models.Auction;
import com.auction.auctionservice.models.AuctionStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuctionService {

    private final Map<UUID, Auction> auctions = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate;

    @Value("${services.user.base-url:http://localhost:3001}")
    private String userServiceBaseUrl;

    @Value("${services.bid.base-url:http://localhost:3004}")
    private String bidServiceBaseUrl;

    @Value("${services.notification.base-url:http://localhost:3005}")
    private String notificationServiceBaseUrl;

    public AuctionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Auction createAuction(String itemName, String sellerId) {
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("itemName is required");
        }
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("sellerId is required");
        }
        ensureUserIsActive(sellerId);

        Auction auction = new Auction(itemName.trim(), sellerId.trim());
        auctions.put(auction.getAuctionId(), auction);

        updateSellerStatus(sellerId, true, auction.getAuctionId().toString());
        broadcastEvent("AUCTION_ADVERTISED", Map.of(
            "auctionId", auction.getAuctionId().toString(),
            "itemName", auction.getItemName(),
            "sellerId", auction.getSellerId(),
            "cycleNumber", auction.getCycleNumber(),
            "startTime", auction.getStartTime().toString(),
            "endTime", auction.getEndTime().toString()
        ));

        return auction;
    }

    public Auction getAuction(UUID auctionId) {
        return auctions.get(auctionId);
    }

    public List<Auction> listAllAuctions() {
        List<Auction> result = new ArrayList<>(auctions.values());
        result.sort(Comparator.comparing(Auction::getStartTime).reversed());
        return result;
    }

    public List<Auction> listActiveAuctions() {
        List<Auction> active = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            if (auction.getStatus() == AuctionStatus.ACTIVE) {
                active.add(auction);
            }
        }
        active.sort(Comparator.comparing(Auction::getEndTime));
        return active;
    }

    public List<Auction> listActiveAuctionsForSeller(String sellerId) {
        List<Auction> active = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            if (auction.getStatus() == AuctionStatus.ACTIVE && auction.getSellerId().equals(sellerId)) {
                active.add(auction);
            }
        }
        return active;
    }

    public List<Auction> findExpiredActiveAuctions() {
        Instant now = Instant.now();
        List<Auction> expired = new ArrayList<>();
        for (Auction auction : auctions.values()) {
            if (auction.getStatus() == AuctionStatus.ACTIVE && !auction.getEndTime().isAfter(now)) {
                expired.add(auction);
            }
        }
        return expired;
    }

    public AuctionStateResponse getAuctionState(UUID auctionId) {
        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            return null;
        }
        AuctionStateResponse response = new AuctionStateResponse();
        response.setAuctionId(auction.getAuctionId());
        response.setItemName(auction.getItemName());
        response.setSellerId(auction.getSellerId());
        response.setStatus(auction.getStatus());
        response.setCycleNumber(auction.getCycleNumber());
        response.setStartTime(auction.getStartTime());
        response.setEndTime(auction.getEndTime());
        return response;
    }

    public void processExpiredAuction(Auction auction) {
        if (!auction.acquireClosingLock()) {
            return;
        }
        try {
            if (auction.getStatus() != AuctionStatus.ACTIVE || auction.getEndTime().isAfter(Instant.now())) {
                return;
            }

            BidWinnerResponse winner = fetchWinnerForCurrentCycle(auction);
            if (winner == null || winner.getBidId() == null || winner.getBidId().isBlank()) {
                restartAuctionCycle(auction);
                return;
            }

            finalizeWithWinner(auction, winner);
        } finally {
            auction.releaseClosingLock();
        }
    }

    private void restartAuctionCycle(Auction auction) {
        int nextCycle = auction.getCycleNumber() + 1;
        auction.setCycleNumber(nextCycle);
        auction.setStartTime(Instant.now());
        auction.setEndTime(auction.getStartTime().plusSeconds(300));

        Map<String, Object> payload = new HashMap<>();
        payload.put("auctionId", auction.getAuctionId().toString());
        payload.put("itemName", auction.getItemName());
        payload.put("sellerId", auction.getSellerId());
        payload.put("cycleNumber", auction.getCycleNumber());
        payload.put("startTime", auction.getStartTime().toString());
        payload.put("endTime", auction.getEndTime().toString());
        broadcastEvent("AUCTION_RESTARTED", payload);
    }

    private void finalizeWithWinner(Auction auction, BidWinnerResponse winner) {
        auction.setWinningBidId(winner.getBidId());
        auction.setWinnerUserId(winner.getBidderId());
        auction.setStatus(AuctionStatus.ENDED);
        auction.setFinalizedAt(Instant.now());

        updateSellerStatus(auction.getSellerId(), false, auction.getAuctionId().toString());
        closeAuctionInBidService(auction.getAuctionId(), auction.getCycleNumber());

        Map<String, Object> payload = new HashMap<>();
        payload.put("auctionId", auction.getAuctionId().toString());
        payload.put("itemName", auction.getItemName());
        payload.put("sellerId", auction.getSellerId());
        payload.put("winnerUserId", winner.getBidderId());
        payload.put("winningBidId", winner.getBidId());
        payload.put("winningAmount", winner.getAmount());
        payload.put("cycleNumber", auction.getCycleNumber());
        payload.put("finalizedAt", auction.getFinalizedAt().toString());

        broadcastEvent("AUCTION_FINALIZED", payload);
        notifySingleUser("AUCTION_WON", winner.getBidderId(), payload);
        notifySingleUser("AUCTION_SOLD", auction.getSellerId(), payload);
    }

    private BidWinnerResponse fetchWinnerForCurrentCycle(Auction auction) {
        String url = String.format(
            "%s/internal/auctions/%s/cycles/%d/winner",
            bidServiceBaseUrl,
            auction.getAuctionId(),
            auction.getCycleNumber()
        );

        try {
            ResponseEntity<BidWinnerResponse> response = restTemplate.getForEntity(url, BidWinnerResponse.class);
            return response.getBody();
        } catch (Exception ex) {
            return null;
        }
    }

    private void closeAuctionInBidService(UUID auctionId, int cycleNumber) {
        String url = String.format("%s/internal/auctions/%s/cycles/%d/close", bidServiceBaseUrl, auctionId, cycleNumber);
        try {
            restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, Map.class);
        } catch (Exception ex) {
            // Best-effort call; auction state remains authoritative in this service.
        }
    }

    private void ensureUserIsActive(String userId) {
        String url = String.format("%s/users/%s", userServiceBaseUrl, userId);
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null || !Boolean.TRUE.equals(body.get("isActive"))) {
                throw new IllegalArgumentException("Seller must be an active registered user");
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Seller must be an active registered user");
        }
    }

    private void updateSellerStatus(String userId, boolean isSelling, String auctionId) {
        String url = String.format("%s/users/%s/update-seller-status", userServiceBaseUrl, userId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("isSelling", isSelling);
        payload.put("auctionId", auctionId);

        try {
            restTemplate.postForEntity(url, payload, Map.class);
        } catch (Exception ex) {
            // Best effort only.
        }
    }

    private void broadcastEvent(String type, Map<String, Object> payload) {
        String url = notificationServiceBaseUrl + "/events";
        NotificationEventRequest request = new NotificationEventRequest(type, "ALL_REGISTERED", null, payload);
        try {
            restTemplate.postForEntity(url, request, Map.class);
        } catch (Exception ex) {
            // Best effort only.
        }
    }

    private void notifySingleUser(String type, String targetUserId, Map<String, Object> payload) {
        String url = notificationServiceBaseUrl + "/events";
        NotificationEventRequest request = new NotificationEventRequest(type, "SINGLE_USER", targetUserId, payload);
        try {
            restTemplate.postForEntity(url, request, Map.class);
        } catch (Exception ex) {
            // Best effort only.
        }
    }}
