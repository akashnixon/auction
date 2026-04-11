package com.auction.bidservice.services;

import com.auction.bidservice.dto.NotificationEventRequest;
import com.auction.bidservice.dto.PlaceBidRequest;
import com.auction.bidservice.models.Bid;
import com.auction.bidservice.repositories.BidRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class BidService {

    private final RestTemplate restTemplate;
    private final BidRepository bidRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${services.user.base-url:http://localhost:3001}")
    private String userServiceBaseUrl;

    @Value("${services.auction.base-url:http://localhost:3003}")
    private String auctionServiceBaseUrl;

    @Value("${services.notification.base-url:http://localhost:3005}")
    private String notificationServiceBaseUrl;

    public BidService(
        RestTemplate restTemplate,
        BidRepository bidRepository,
        StringRedisTemplate redisTemplate,
        ObjectMapper objectMapper
    ) {
        this.restTemplate = restTemplate;
        this.bidRepository = bidRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public synchronized Map<String, Object> placeBid(PlaceBidRequest request) {
        validateBidRequest(request);

        String auctionId = request.getAuctionId().trim();
        String bidderId = request.getBidderId().trim();
        BigDecimal amount = BigDecimal.valueOf(request.getAmount());
        String idempotencyKey = request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()
            ? UUID.randomUUID().toString()
            : request.getIdempotencyKey().trim();

        Map<?, ?> user = fetchUser(bidderId);
        boolean active = Boolean.TRUE.equals(user.get("isActive")) || Boolean.TRUE.equals(user.get("active"));
        if (!active) {
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

        Optional<Bid> existing = bidRepository.findByAuctionIdAndBidderIdAndIdempotencyKey(auctionId, bidderId, idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get(), isCurrentHighest(existing.get()));
        }

        Bid previousHighest = getHighestFromDatabase(auctionId, cycleNumber).orElse(null);

        Bid bid = new Bid();
        bid.setBidId(UUID.randomUUID().toString());
        bid.setAuctionId(auctionId);
        bid.setCycleNumber(cycleNumber);
        bid.setBidderId(bidderId);
        bid.setAmount(amount);
        bid.setReceivedAt(Instant.now());
        bid.setIdempotencyKey(idempotencyKey);

        try {
            bid = bidRepository.save(bid);
        } catch (DataIntegrityViolationException ex) {
            Bid alreadySaved = bidRepository
                .findByAuctionIdAndBidderIdAndIdempotencyKey(auctionId, bidderId, idempotencyKey)
                .orElseThrow();
            return toResponse(alreadySaved, isCurrentHighest(alreadySaved));
        }

        Bid currentHighest = getHighestFromDatabase(auctionId, cycleNumber).orElse(null);
        cacheHighestBid(auctionId, cycleNumber, currentHighest);

        if (currentHighest != null) {
            boolean highestChanged = previousHighest == null || !Objects.equals(previousHighest.getBidId(), currentHighest.getBidId());
            if (highestChanged) {
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
        }

        return toResponse(bid, currentHighest != null && Objects.equals(currentHighest.getBidId(), bid.getBidId()));
    }

    public List<Bid> listBidsForAuction(String auctionId) {
        return bidRepository.findByAuctionIdOrderByReceivedAtAsc(auctionId);
    }

    public Map<String, Object> listActiveHighestForUser(String userId) {
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> activeHighest = new ArrayList<>();

        for (Bid bid : bidRepository.findAll()) {
            String key = cycleKey(bid.getAuctionId(), bid.getCycleNumber());
            if (!seen.add(key)) {
                continue;
            }

            Optional<Bid> highestOpt = getHighestFromDatabase(bid.getAuctionId(), bid.getCycleNumber());
            if (highestOpt.isEmpty()) {
                continue;
            }

            Bid highest = highestOpt.get();
            if (!Objects.equals(highest.getBidderId(), userId)) {
                continue;
            }

            try {
                Map<?, ?> auctionState = fetchAuctionState(highest.getAuctionId());
                if ("ACTIVE".equals(String.valueOf(auctionState.get("status")))) {
                    activeHighest.add(Map.of(
                        "auctionId", highest.getAuctionId(),
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
        Optional<Bid> winner = getHighestFromDatabase(auctionId, cycleNumber);
        if (winner.isEmpty()) {
            return Map.of();
        }

        Bid bid = winner.get();
        cacheHighestBid(auctionId, cycleNumber, bid);
        return Map.of(
            "bidId", bid.getBidId(),
            "bidderId", bid.getBidderId(),
            "amount", String.valueOf(bid.getAmount()),
            "receivedAt", bid.getReceivedAt()
        );
    }

    public Map<String, Object> closeCycle(String auctionId, int cycleNumber) {
        Optional<Bid> winner = getHighestFromDatabase(auctionId, cycleNumber);
        winner.ifPresent(bid -> updateUserBidderStatus(bid.getBidderId(), false, auctionId));

        redisTemplate.delete(redisKey(auctionId, cycleNumber));

        publishEvent("AUCTION_CYCLE_CLOSED", Map.of(
            "auctionId", auctionId,
            "cycleNumber", cycleNumber,
            "winnerBidId", winner.map(Bid::getBidId).orElse(null),
            "winnerUserId", winner.map(Bid::getBidderId).orElse(null)
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

    private Optional<Bid> getHighestFromDatabase(String auctionId, int cycleNumber) {
        List<Bid> ordered = bidRepository.findHighestForCycleOrdered(auctionId, cycleNumber);
        return ordered.isEmpty() ? Optional.empty() : Optional.of(ordered.get(0));
    }

    private boolean isCurrentHighest(Bid bid) {
        return getHighestFromDatabase(bid.getAuctionId(), bid.getCycleNumber())
            .map(top -> Objects.equals(top.getBidId(), bid.getBidId()))
            .orElse(false);
    }

    private String cycleKey(String auctionId, int cycleNumber) {
        return auctionId + ":" + cycleNumber;
    }

    private String redisKey(String auctionId, int cycleNumber) {
        return "highest:" + cycleKey(auctionId, cycleNumber);
    }

    private void cacheHighestBid(String auctionId, int cycleNumber, Bid highest) {
        String key = redisKey(auctionId, cycleNumber);
        if (highest == null) {
            redisTemplate.delete(key);
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("bidId", highest.getBidId());
        payload.put("bidderId", highest.getBidderId());
        payload.put("amount", highest.getAmount());
        payload.put("receivedAt", highest.getReceivedAt());

        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ignored) {
        }
    }

    private Map<String, Object> toResponse(Bid bid, boolean isHighestBid) {
        Map<String, Object> response = new HashMap<>();
        response.put("bidId", bid.getBidId());
        response.put("auctionId", bid.getAuctionId());
        response.put("cycleNumber", bid.getCycleNumber());
        response.put("bidderId", bid.getBidderId());
        response.put("amount", bid.getAmount());
        response.put("receivedAt", bid.getReceivedAt());
        response.put("isHighestBid", isHighestBid);
        response.put("idempotencyKey", bid.getIdempotencyKey());
        return response;
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
