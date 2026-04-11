package com.auction.authservice.model;

import java.time.Instant;

public class ValidateResponse {
    private boolean valid;
    private String userId;
    private String username;
    private Instant issuedAt;
    private Instant expiresAt;
    private String error;

    public static ValidateResponse success(String userId, String username, Instant issuedAt, Instant expiresAt) {
        ValidateResponse response = new ValidateResponse();
        response.valid = true;
        response.userId = userId;
        response.username = username;
        response.issuedAt = issuedAt;
        response.expiresAt = expiresAt;
        return response;
    }

    public static ValidateResponse failure(String error) {
        ValidateResponse response = new ValidateResponse();
        response.valid = false;
        response.error = error;
        return response;
    }

    public boolean isValid() {
        return valid;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getError() {
        return error;
    }
}
