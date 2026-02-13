package com.auction.unified.controllers;

import com.auction.unified.dto.DeregisterRequest;
import com.auction.unified.dto.RegisterUserRequest;
import com.auction.unified.dto.UpdateBidderStatusRequest;
import com.auction.unified.dto.UpdateSellerStatusRequest;
import com.auction.unified.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/users/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping("/users/deregister")
    public ResponseEntity<Map<String, Object>> deregister(@RequestBody DeregisterRequest request) {
        return ResponseEntity.ok(userService.deregister(request));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> users() {
        return ResponseEntity.ok(userService.listUsers());
    }

    @PostMapping("/users/{userId}/update-seller-status")
    public ResponseEntity<Map<String, Object>> updateSeller(
        @PathVariable String userId,
        @RequestBody UpdateSellerStatusRequest request
    ) {
        return ResponseEntity.ok(userService.updateSellerStatus(userId, request));
    }

    @PostMapping("/users/{userId}/update-bidder-status")
    public ResponseEntity<Map<String, Object>> updateBidder(
        @PathVariable String userId,
        @RequestBody UpdateBidderStatusRequest request
    ) {
        return ResponseEntity.ok(userService.updateBidderStatus(userId, request));
    }
}
