package com.auction.userservice.controller;

import com.auction.userservice.model.ApiError;
import com.auction.userservice.model.AuthUserResponse;
import com.auction.userservice.model.BidderStatusRequest;
import com.auction.userservice.model.DeregisterRequest;
import com.auction.userservice.model.RegisterRequest;
import com.auction.userservice.model.SellerStatusRequest;
import com.auction.userservice.model.User;
import com.auction.userservice.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.getUsername().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            return ResponseEntity.badRequest().body(new ApiError("Username already exists"));
        }

        String id = UUID.randomUUID().toString();
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(request.getEmail().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setActive(true);
        user.setSelling(false);
        user.setHighestBidder(false);
        user.setCreatedAt(Instant.now());
        userRepository.save(user);

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
        User user = userRepository.findById(request.getUserId()).orElse(null);
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
        userRepository.save(user);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("isActive", user.isActive());
        response.put("deregisteredAt", user.getDeregisteredAt());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUser(@PathVariable String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(new ApiError("User not found"));
        }

        return ResponseEntity.ok(user);
    }

    @GetMapping
    public ResponseEntity<?> listUsers() {
        return ResponseEntity.ok(Map.of("users", userRepository.findAll()));
    }

    @GetMapping("/internal/by-username/{username}")
    public ResponseEntity<?> getUserForAuth(@PathVariable String username) {
        return userRepository.findByUsernameIgnoreCase(username.trim().toLowerCase(Locale.ROOT))
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(new AuthUserResponse(user)))
            .orElseGet(() -> ResponseEntity.status(404).body(new ApiError("User not found")));
    }

    @PostMapping("/{userId}/update-seller-status")
    public ResponseEntity<?> updateSellerStatus(@PathVariable String userId,
                                                @Valid @RequestBody SellerStatusRequest request) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(new ApiError("User not found"));
        }

        user.setSelling(request.getIsSelling());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("id", user.getId(), "isSelling", user.isSelling()));
    }

    @PostMapping("/{userId}/update-bidder-status")
    public ResponseEntity<?> updateBidderStatus(@PathVariable String userId,
                                                @Valid @RequestBody BidderStatusRequest request) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(new ApiError("User not found"));
        }

        user.setHighestBidder(request.getIsHighestBidder());
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("id", user.getId(), "isHighestBidder", user.isHighestBidder()));
    }
}
