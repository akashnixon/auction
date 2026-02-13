package com.auction.unified.services;

import com.auction.unified.dto.DeregisterRequest;
import com.auction.unified.dto.RegisterUserRequest;
import com.auction.unified.dto.UpdateBidderStatusRequest;
import com.auction.unified.dto.UpdateSellerStatusRequest;
import com.auction.unified.models.UserRecord;
import com.auction.unified.store.InMemoryStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class UserService {
    private final InMemoryStore store;

    public UserService(InMemoryStore store) {
        this.store = store;
    }

    public Map<String, Object> register(RegisterUserRequest request) {
        String username = Optional.ofNullable(request.getUsername()).orElse(request.getName());
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username (or name) is required");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        if (findByUsername(username.trim()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        UserRecord user = new UserRecord();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(username.trim());
        user.setEmail(request.getEmail().trim());
        user.setActive(true);
        user.setCreatedAt(Instant.now().toString());

        store.users.put(user.getId(), user);
        store.credentialPasswords.put(user.getUsername(), request.getPassword() == null ? "password123" : request.getPassword());

        return serialize(user);
    }

    public Map<String, Object> deregister(DeregisterRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

        UserRecord user = store.users.get(request.getUserId());
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }
        if (!user.isActive()) {
            throw new IllegalStateException("User is already deregistered");
        }
        if (!user.getActiveSellingAuctions().isEmpty()) {
            throw new IllegalStateException("Cannot deregister: user is currently offering an active auction item");
        }
        if (!user.getActiveLeadingAuctions().isEmpty()) {
            throw new IllegalStateException("Cannot deregister: user is currently leading at least one active auction");
        }

        user.setActive(false);
        user.setDeregisteredAt(Instant.now().toString());
        return serialize(user);
    }

    public Map<String, Object> getUser(String userId) {
        UserRecord user = store.users.get(userId);
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }
        return serialize(user);
    }

    public Map<String, Object> listUsers() {
        List<Map<String, Object>> users = new ArrayList<>();
        for (UserRecord user : store.users.values()) {
            users.add(serialize(user));
        }
        return Map.of("users", users, "total", users.size());
    }

    public Map<String, Object> updateSellerStatus(String userId, UpdateSellerStatusRequest request) {
        UserRecord user = store.users.get(userId);
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }
        if (request.getIsSelling() == null) {
            throw new IllegalArgumentException("isSelling must be a boolean");
        }

        if (request.getAuctionId() != null && !request.getAuctionId().isBlank()) {
            if (request.getIsSelling()) {
                user.getActiveSellingAuctions().add(request.getAuctionId());
            } else {
                user.getActiveSellingAuctions().remove(request.getAuctionId());
            }
        } else if (!request.getIsSelling()) {
            user.getActiveSellingAuctions().clear();
        }

        return Map.of(
            "userId", user.getId(),
            "isSelling", !user.getActiveSellingAuctions().isEmpty(),
            "activeSellingAuctionCount", user.getActiveSellingAuctions().size(),
            "activeSellingAuctionIds", user.getActiveSellingAuctions()
        );
    }

    public Map<String, Object> updateBidderStatus(String userId, UpdateBidderStatusRequest request) {
        UserRecord user = store.users.get(userId);
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }
        if (request.getIsHighestBidder() == null) {
            throw new IllegalArgumentException("isHighestBidder must be a boolean");
        }

        if (request.getAuctionId() != null && !request.getAuctionId().isBlank()) {
            if (request.getIsHighestBidder()) {
                user.getActiveLeadingAuctions().add(request.getAuctionId());
            } else {
                user.getActiveLeadingAuctions().remove(request.getAuctionId());
            }
        } else if (!request.getIsHighestBidder()) {
            user.getActiveLeadingAuctions().clear();
        }

        return Map.of(
            "userId", user.getId(),
            "isHighestBidder", !user.getActiveLeadingAuctions().isEmpty(),
            "activeLeadingAuctionCount", user.getActiveLeadingAuctions().size(),
            "activeLeadingAuctionIds", user.getActiveLeadingAuctions()
        );
    }

    public Optional<UserRecord> findByUsername(String username) {
        return store.users.values().stream()
            .filter(user -> user.getUsername().equals(username))
            .findFirst();
    }

    public boolean isActiveUser(String userId) {
        UserRecord user = store.users.get(userId);
        return user != null && user.isActive();
    }

    public String getStoredPassword(String username) {
        return store.credentialPasswords.get(username);
    }

    private Map<String, Object> serialize(UserRecord user) {
        return Map.of(
            "id", user.getId(),
            "username", user.getUsername(),
            "email", user.getEmail(),
            "isActive", user.isActive(),
            "isSelling", !user.getActiveSellingAuctions().isEmpty(),
            "isHighestBidder", !user.getActiveLeadingAuctions().isEmpty(),
            "createdAt", user.getCreatedAt(),
            "deregisteredAt", user.getDeregisteredAt() == null ? "" : user.getDeregisteredAt()
        );
    }
}
