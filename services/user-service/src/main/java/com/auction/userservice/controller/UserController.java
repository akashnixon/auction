package com.auction.userservice.controller;

import com.auction.userservice.model.ApiError;
import com.auction.userservice.model.BidderStatusRequest;
import com.auction.userservice.model.DeregisterRequest;
import com.auction.userservice.model.RegisterRequest;
import com.auction.userservice.model.SellerStatusRequest;
import com.auction.userservice.model.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/users")
public class UserController {
    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, String> userIdByUsername = new ConcurrentHashMap<>();

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.getUsername().trim().toLowerCase(Locale.ROOT);
        if (userIdByUsername.containsKey(username)) {
            return ResponseEntity.badRequest().body(new ApiError("Username already exists"));
        }

        String id = UUID.randomUUID().toString();
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(request.getEmail().trim());
        user.setActive(true);
        user.setSelling(false);
        user.setHighestBidder(false);
        user.setCreatedAt(Instant.now());

        usersById.put(id, user);
        userIdByUsername.put(username, id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("isActive", user.isActive());
        response.put("createdAt", user.getCreatedAt());

        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/deregister")
    public ResponseEntity<?> deregister(@Valid @RequestBody DeregisterRequest request) {
        User user = usersById.get(request.getUserId());
        if (user == null) {
            return ResponseEntity.status(404).body(new ApiError("User not found"));
        }
        if (!user.isActive()) {
            return ResponseEntity.badRequest().body(new ApiError("User is already deregistered"));
        }
        if (user.isSelling()) {
            return ResponseEntity.status(409).body(new ApiError("Cannot deregister: user is currently selling an item", "active_seller"));
        }
        if (user.isHighestBidder()) {
            return ResponseEntity.status(409).body(new ApiError("Cannot deregister: user is highest bidder in active auctions", "active_bidder"));
        }

        user.setActive(false);
        user.setDeregisteredAt(Instant.now());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("isActive", user.isActive());
        response.put("deregisteredAt", user.getDeregisteredAt());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUser(@PathVariable String userId) {
        User user = usersById.get(userId);
        if (user == null) {
            return ResponseEntity.status(404).body(new ApiError("User not found"));
        }

        return ResponseEntity.ok(user);
    }

    @GetMapping
    public ResponseEntity<?> listUsers() {
        return ResponseEntity.ok(Map.of("users", new ArrayList<>(usersById.values())));
    }

    @PostMapping("/{userId}/update-seller-status")
    public ResponseEntity<?> updateSellerStatus(@PathVariable String userId,
                                                @Valid @RequestBody SellerStatusRequest request) {
        User user = usersById.get(userId);
        if (user == null) {
            return ResponseEntity.status(404).body(new ApiError("User not found"));
        }

        user.setSelling(request.getIsSelling());
        return ResponseEntity.ok(Map.of("id", user.getId(), "isSelling", user.isSelling()));
    }

    @PostMapping("/{userId}/update-bidder-status")
    public ResponseEntity<?> updateBidderStatus(@PathVariable String userId,
                                                @Valid @RequestBody BidderStatusRequest request) {
        User user = usersById.get(userId);
        if (user == null) {
            return ResponseEntity.status(404).body(new ApiError("User not found"));
        }

        user.setHighestBidder(request.getIsHighestBidder());
        return ResponseEntity.ok(Map.of("id", user.getId(), "isHighestBidder", user.isHighestBidder()));
    }

}
