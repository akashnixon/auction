package com.auction.unified.services;

import com.auction.unified.dto.LoginRequest;
import com.auction.unified.dto.TokenValidationRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class AuthService {
    private final UserService userService;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${security.jwt.expiry-seconds:86400}")
    private long expirySeconds;

    public AuthService(UserService userService) {
        this.userService = userService;
    }

    public Map<String, Object> login(LoginRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            throw new IllegalArgumentException("Username and password are required");
        }

        var userOpt = userService.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            throw new NoSuchElementException("Invalid username or password");
        }

        String storedPassword = userService.getStoredPassword(request.getUsername());
        if (storedPassword == null || !storedPassword.equals(request.getPassword())) {
            throw new NoSuchElementException("Invalid username or password");
        }

        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirySeconds);

        String token = Jwts.builder()
            .subject(userOpt.get().getId())
            .claim("username", userOpt.get().getUsername())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(getSigningKey())
            .compact();

        return Map.of(
            "token", token,
            "userId", userOpt.get().getId(),
            "username", userOpt.get().getUsername(),
            "expiresIn", expirySeconds + "s"
        );
    }

    public Map<String, Object> validate(TokenValidationRequest request) {
        if (request.getToken() == null || request.getToken().isBlank()) {
            throw new IllegalArgumentException("Token is required");
        }
        Claims claims = parseClaims(request.getToken());
        return Map.of(
            "valid", true,
            "userId", claims.getSubject(),
            "username", claims.get("username", String.class),
            "expiresAt", claims.getExpiration().toInstant().getEpochSecond()
        );
    }

    public Map<String, Object> verifyAuthorizationHeader(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        Claims claims = parseClaims(token);
        return Map.of(
            "userId", claims.getSubject(),
            "username", claims.get("username", String.class),
            "expiresAt", claims.getExpiration().toInstant().getEpochSecond()
        );
    }

    public String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Access token required");
        }
        return authorizationHeader.substring("Bearer ".length());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception ex) {
            keyBytes = jwtSecret.getBytes();
        }
        return Keys.hmacShaKeyFor(keyBytes.length >= 32 ? keyBytes : padKey(keyBytes));
    }

    private byte[] padKey(byte[] keyBytes) {
        byte[] padded = new byte[32];
        for (int i = 0; i < padded.length; i++) {
            padded[i] = i < keyBytes.length ? keyBytes[i] : (byte) 'x';
        }
        return padded;
    }
}
