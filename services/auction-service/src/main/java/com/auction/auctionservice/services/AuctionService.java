package com.auction.auctionservice.services;

import com.auction.auctionservice.dto.AuctionStateResponse;
import com.auction.auctionservice.dto.AuthValidationResponse;
import com.auction.auctionservice.dto.BidWinnerResponse;
import com.auction.auctionservice.dto.NotificationEventRequest;
import com.auction.auctionservice.models.Auction;
import com.auction.auctionservice.models.AuctionStatus;
import com.auction.auctionservice.repositories.AuctionRepository;
import com.auction.auctionservice.util.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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

    @Value("${services.auth.base-url:http://localhost:3002}")
    private String authServiceBaseUrl;

    @Value("${auction.duration-seconds:30}")
    private long auctionDurationSeconds;

    public AuctionService(AuctionRepository auctionRepository, RestTemplate restTemplate) {
        this.auctionRepository = auctionRepository;
        this.restTemplate = restTemplate;
    }

    public Auction createAuction(
        String itemName,
        String sellerId,
        String imageDataUrl,
        Double startingPrice,
        String authorizationHeader
    ) {
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("itemName is required");
        }
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("sellerId is required");
        }
        if (startingPrice == null || startingPrice < 0) {
            throw new IllegalArgumentException("startingPrice must be zero or greater");
        }
        ensureAuthenticatedActiveUser(authorizationHeader, sellerId, "seller");

        Auction auction = new Auction();
        auction.setAuctionId(IdGenerator.auctionId(itemName));
        auction.setItemName(itemName.trim());
        auction.setSellerId(sellerId.trim());
        auction.setImageDataUrl(imageDataUrl);
        auction.setStartingPrice(BigDecimal.valueOf(startingPrice));
        auction.setCycleNumber(1);
        auction.setStartTime(Instant.now());
        auction.setEndTime(auction.getStartTime().plusSeconds(auctionDurationSeconds));
        auction.setStatus(AuctionStatus.ACTIVE);

        auction = auctionRepository.save(auction);

        updateSellerStatus(sellerId, true, auction.getAuctionId());
        broadcastEvent("AUCTION_ADVERTISED", buildAuctionEventPayload(auction));

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
        response.setImageDataUrl(auction.getImageDataUrl());
        response.setStartingPrice(auction.getStartingPrice());
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
        auction.setEndTime(auction.getStartTime().plusSeconds(auctionDurationSeconds));
        auctionRepository.save(auction);

        Map<String, Object> payload = buildAuctionEventPayload(auction);
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

        Map<String, Object> payload = buildAuctionEventPayload(auction);
        payload.put("winnerUserId", winner.getBidderId());
        payload.put("winningBidId", winner.getBidId());
        payload.put("winningAmount", winner.getAmount());
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

    private void ensureAuthenticatedActiveUser(String authorizationHeader, String userId, String actorRole) {
        AuthValidationResponse auth = validateAuthorizationHeader(authorizationHeader);
        String url = String.format("%s/users/%s", userServiceBaseUrl, userId);
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<?, ?> body = response.getBody();
            boolean active = false;
            String username = null;
            if (body != null) {
                Object isActive = body.get("isActive");
                Object activeField = body.get("active");
                active = Boolean.TRUE.equals(isActive) || Boolean.TRUE.equals(activeField);
                Object usernameField = body.get("username");
                username = usernameField == null ? null : String.valueOf(usernameField);
            }
            if (!active) {
                throw new IllegalArgumentException(capitalize(actorRole) + " must be an active registered user");
            }
            if (username == null || auth.getUsername() == null || !username.equalsIgnoreCase(auth.getUsername())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Authenticated user does not match " + actorRole + "Id");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(capitalize(actorRole) + " must be an active registered user");
        }
    }

    private AuthValidationResponse validateAuthorizationHeader(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        String url = authServiceBaseUrl + "/auth/validate";
        Map<String, String> payload = Map.of("token", token);

        try {
            ResponseEntity<AuthValidationResponse> response = restTemplate.postForEntity(url, payload, AuthValidationResponse.class);
            AuthValidationResponse body = response.getBody();
            if (body == null || !body.isValid()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
            }
            return body;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        return token;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private Map<String, Object> buildAuctionEventPayload(Auction auction) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("auctionId", auction.getAuctionId());
        payload.put("itemName", auction.getItemName());
        payload.put("sellerId", auction.getSellerId());
        payload.put("imageDataUrl", auction.getImageDataUrl());
        payload.put("startingPrice", auction.getStartingPrice());
        payload.put("cycleNumber", auction.getCycleNumber());
        payload.put("startTime", auction.getStartTime() == null ? null : auction.getStartTime().toString());
        payload.put("endTime", auction.getEndTime() == null ? null : auction.getEndTime().toString());
        return payload;
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
