package com.auction.authservice.controller;

import com.auction.authservice.model.ApiError;
import com.auction.authservice.model.AuthenticatedUser;
import com.auction.authservice.model.LoginRequest;
import com.auction.authservice.model.TokenResponse;
import com.auction.authservice.model.ValidateRequest;
import com.auction.authservice.model.ValidateResponse;
import com.auction.authservice.security.AuthTokenFilter;
import com.auction.authservice.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final Map<String, DemoUser> DEMO_USERS = Map.of(
            "john_doe", new DemoUser("user-001", "password123"),
            "jane_smith", new DemoUser("user-002", "securepass456")
    );

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        DemoUser demoUser = DEMO_USERS.get(request.getUsername());
        if (demoUser == null || !demoUser.password().equals(request.getPassword())) {
            return ResponseEntity.status(401).body(new ApiError("Invalid credentials"));
        }

        AuthenticatedUser user = new AuthenticatedUser(demoUser.userId(), request.getUsername());
        String token = jwtService.generateToken(user);
        Instant issuedAt = Instant.now();

        ResponseCookie cookie = ResponseCookie.from("auth_token", token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofSeconds(jwtService.getExpirationSeconds()))
                .build();

        TokenResponse response = new TokenResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                issuedAt,
                user.userId(),
                user.username()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/validate")
    public ResponseEntity<ValidateResponse> validate(@RequestBody(required = false) ValidateRequest request,
                                                     HttpServletRequest httpRequest) {
        String token = request != null ? request.getToken() : null;
        if (token == null || token.isBlank()) {
            token = extractTokenFromRequest(httpRequest);
        }

        if (token == null || token.isBlank()) {
            return ResponseEntity.ok(ValidateResponse.failure("Missing token"));
        }

        try {
            Claims claims = jwtService.parseClaims(token);
            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            Instant issuedAt = claims.getIssuedAt().toInstant();
            Instant expiresAt = claims.getExpiration().toInstant();
            return ResponseEntity.ok(ValidateResponse.success(userId, username, issuedAt, expiresAt));
        } catch (Exception ex) {
            return ResponseEntity.ok(ValidateResponse.failure("Invalid token"));
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verify(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token == null) {
            return ResponseEntity.status(401).body(new ApiError("Missing token"));
        }

        try {
            jwtService.parseClaims(token);
            return ResponseEntity.ok(Map.of("status", "valid"));
        } catch (Exception ex) {
            return ResponseEntity.status(401).body(new ApiError("Invalid token"));
        }
    }

    @GetMapping("/protected-example")
    public ResponseEntity<?> protectedExample(HttpServletRequest request) {
        Object authUser = request.getAttribute(AuthTokenFilter.AUTH_USER_ATTR);
        if (authUser instanceof AuthenticatedUser user) {
            return ResponseEntity.ok(Map.of(
                    "message", "Access granted",
                    "userId", user.userId(),
                    "username", user.username()
            ));
        }
        return ResponseEntity.status(401).body(new ApiError("Missing or invalid token"));
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("auth_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    private record DemoUser(String userId, String password) {
    }
}
