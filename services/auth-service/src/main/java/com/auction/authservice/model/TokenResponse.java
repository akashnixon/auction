package com.auction.authservice.model;

import java.time.Instant;

public class TokenResponse {
    private String token;
    private String tokenType;
    private long expiresIn;
    private Instant issuedAt;
    private String userId;
    private String username;

    public TokenResponse(String token, String tokenType, long expiresIn, Instant issuedAt, String userId, String username) {
        this.token = token;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.issuedAt = issuedAt;
        this.userId = userId;
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }
}
