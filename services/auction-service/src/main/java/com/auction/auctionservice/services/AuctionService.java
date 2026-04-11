package com.auction.auctionservice.services;

import com.auction.auctionservice.dto.AuctionStateResponse;
import com.auction.auctionservice.dto.BidWinnerResponse;
import com.auction.auctionservice.dto.NotificationEventRequest;
import com.auction.auctionservice.models.Auction;
import com.auction.auctionservice.models.AuctionStatus;
import com.auction.auctionservice.repositories.AuctionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final RestTemplate restTemplate;
    private final Map<String, ReentrantLock> closeLocks = new ConcurrentHashMap<>();

    @Value("${services.user.base-url:http://localhost:3001}")
    private String userServiceBaseUrl;

    @Value("${services.bid.base-url:http://localhost:3004}")
    private String bidServiceBaseUrl;

    @Value("${services.notification.base-url:http://localhost:3005}")
    private String notificationServiceBaseUrl;

    public AuctionService(AuctionRepository auctionRepository, RestTemplate restTemplate) {
        this.auctionRepository = auctionRepository;
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

        Auction auction = new Auction();
        auction.setAuctionId(UUID.randomUUID().toString());
        auction.setItemName(itemName.trim());
        auction.setSellerId(sellerId.trim());
        auction.setCycleNumber(1);
        auction.setStartTime(Instant.now());
        auction.setEndTime(auction.getStartTime().plusSeconds(300));
        auction.setStatus(AuctionStatus.ACTIVE);

        auction = auctionRepository.save(auction);

        updateSellerStatus(sellerId, true, auction.getAuctionId());
        broadcastEvent("AUCTION_ADVERTISED", Map.of(
            "auctionId", auction.getAuctionId(),
            "itemName", auction.getItemName(),
            "sellerId", auction.getSellerId(),
            "cycleNumber", auction.getCycleNumber(),
            "startTime", auction.getStartTime().toString(),
            "endTime", auction.getEndTime().toString()
        ));

        return auction;
    }

    public Auction getAuction(String auctionId) {
        return auctionRepository.findById(auctionId).orElse(null);
    }

    public List<Auction> listAllAuctions() {
        List<Auction> result = auctionRepository.findAll();
        result.sort(Comparator.comparing(Auction::getStartTime).reversed());
        return result;
    }

    public List<Auction> listActiveAuctions() {
        return auctionRepository.findByStatusOrderByStartTimeDesc(AuctionStatus.ACTIVE);
    }

    public List<Auction> listActiveAuctionsForSeller(String sellerId) {
        return auctionRepository.findBySellerIdAndStatus(sellerId, AuctionStatus.ACTIVE);
    }

    public List<Auction> findExpiredActiveAuctions() {
        return auctionRepository.findByStatusAndEndTimeLessThanEqual(AuctionStatus.ACTIVE, Instant.now());
    }

    public AuctionStateResponse getAuctionState(String auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElse(null);
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
        ReentrantLock lock = closeLocks.computeIfAbsent(auction.getAuctionId(), key -> new ReentrantLock());
        if (!lock.tryLock()) {
            return;
        }

        try {
            Auction latest = auctionRepository.findById(auction.getAuctionId()).orElse(null);
            if (latest == null || latest.getStatus() != AuctionStatus.ACTIVE || latest.getEndTime().isAfter(Instant.now())) {
                return;
            }

            BidWinnerResponse winner = fetchWinnerForCurrentCycle(latest);
            if (winner == null || winner.getBidId() == null || winner.getBidId().isBlank()) {
                restartAuctionCycle(latest);
                return;
            }

            finalizeWithWinner(latest, winner);
        } finally {
            lock.unlock();
        }
    }

    private void restartAuctionCycle(Auction auction) {
        auction.setCycleNumber(auction.getCycleNumber() + 1);
        auction.setStartTime(Instant.now());
        auction.setEndTime(auction.getStartTime().plusSeconds(300));
        auctionRepository.save(auction);

        Map<String, Object> payload = new HashMap<>();
        payload.put("auctionId", auction.getAuctionId());
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
        auctionRepository.save(auction);

        updateSellerStatus(auction.getSellerId(), false, auction.getAuctionId());
        closeAuctionInBidService(auction.getAuctionId(), auction.getCycleNumber());

        Map<String, Object> payload = new HashMap<>();
        payload.put("auctionId", auction.getAuctionId());
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

    private void closeAuctionInBidService(String auctionId, int cycleNumber) {
        String url = String.format("%s/internal/auctions/%s/cycles/%d/close", bidServiceBaseUrl, auctionId, cycleNumber);
        try {
            restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, Map.class);
        } catch (Exception ex) {
            // Best effort call; auction state remains authoritative in this service.
        }
    }

    private void ensureUserIsActive(String userId) {
        String url = String.format("%s/users/%s", userServiceBaseUrl, userId);
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<?, ?> body = response.getBody();
            boolean active = false;
            if (body != null) {
                Object isActive = body.get("isActive");
                Object activeField = body.get("active");
                active = Boolean.TRUE.equals(isActive) || Boolean.TRUE.equals(activeField);
            }
            if (!active) {
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
    }
}
