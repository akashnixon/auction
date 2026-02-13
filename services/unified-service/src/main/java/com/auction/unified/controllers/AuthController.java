package com.auction.unified.controllers;

import com.auction.unified.dto.LoginRequest;
import com.auction.unified.dto.TokenValidationRequest;
import com.auction.unified.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/auth/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestBody TokenValidationRequest request) {
        return ResponseEntity.ok(authService.validate(request));
    }

    @GetMapping("/auth/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(authService.verifyAuthorizationHeader(authHeader));
    }

    @GetMapping("/auth/protected-example")
    public ResponseEntity<Map<String, Object>> protectedExample(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> verified = authService.verifyAuthorizationHeader(authHeader);
        return ResponseEntity.ok(Map.of(
            "message", "You have accessed protected resource",
            "userId", verified.get("userId"),
            "username", verified.get("username")
        ));
    }
}
