package com.auction.authservice.controller;

import com.auction.authservice.model.ApiError;
import com.auction.authservice.model.AuthenticatedUser;
import com.auction.authservice.model.LoginRequest;
import com.auction.authservice.model.ServiceTokenRequest;
import com.auction.authservice.model.TokenResponse;
import com.auction.authservice.model.ValidateRequest;
import com.auction.authservice.model.ValidateResponse;
import com.auction.authservice.security.AuthTokenFilter;
import com.auction.authservice.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final Map<String, DemoUser> DEMO_USERS = Map.of(
            "john_doe", new DemoUser("user-001", "password123"),
            "jane_smith", new DemoUser("user-002", "securepass456")
    );
    private static final int LOGIN_MAX_ATTEMPTS = 5;
    private static final Duration LOGIN_ATTEMPT_WINDOW = Duration.ofMinutes(1);

    private final JwtService jwtService;
    private final String internalServiceSecret;
    private final Map<String, Deque<Instant>> failedLoginAttemptsByClient = new ConcurrentHashMap<>();

    public AuthController(JwtService jwtService,
                          @Value("${internal.service.secret}") String internalServiceSecret) {
        this.jwtService = jwtService;
        this.internalServiceSecret = internalServiceSecret;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        String username = request.getUsername().trim().toLowerCase();
        String clientKey = buildClientKey(httpRequest, username);

        if (isRateLimited(clientKey)) {
            return ResponseEntity.status(429).body(new ApiError("Too many login attempts. Try again shortly."));
        }

        DemoUser demoUser = DEMO_USERS.get(username);
        if (demoUser == null || !demoUser.password().equals(request.getPassword())) {
            registerFailedAttempt(clientKey);
            return ResponseEntity.status(401).body(new ApiError("Invalid credentials"));
        }

        clearFailedAttempts(clientKey);

        AuthenticatedUser user = new AuthenticatedUser(demoUser.userId(), username);
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

    @PostMapping("/service-token")
    public ResponseEntity<?> issueServiceToken(@Valid @RequestBody ServiceTokenRequest request,
                                               @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuthHeader) {
        if (!safeEquals(internalAuthHeader, internalServiceSecret)) {
            return ResponseEntity.status(401).body(new ApiError("Invalid internal authentication secret"));
        }

        String serviceName = request.getServiceName().trim().toLowerCase();
        String token = jwtService.generateServiceToken(serviceName);
        Instant issuedAt = Instant.now();

        return ResponseEntity.ok(Map.of(
                "token", token,
                "tokenType", "Bearer",
                "expiresIn", jwtService.getExpirationSeconds(),
                "issuedAt", issuedAt,
                "serviceName", serviceName,
                "role", "SERVICE"
        ));
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

    private String buildClientKey(HttpServletRequest request, String normalizedUsername) {
        String ip = request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
        return normalizedUsername + "|" + ip;
    }

    private boolean isRateLimited(String clientKey) {
        Deque<Instant> attempts = failedLoginAttemptsByClient.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
        Instant now = Instant.now();
        Instant threshold = now.minus(LOGIN_ATTEMPT_WINDOW);

        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(threshold)) {
                attempts.pollFirst();
            }
            return attempts.size() >= LOGIN_MAX_ATTEMPTS;
        }
    }

    private void registerFailedAttempt(String clientKey) {
        Deque<Instant> attempts = failedLoginAttemptsByClient.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            attempts.offerLast(Instant.now());
        }
    }

    private void clearFailedAttempts(String clientKey) {
        failedLoginAttemptsByClient.remove(clientKey);
    }

    private boolean safeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private record DemoUser(String userId, String password) {
    }
}
