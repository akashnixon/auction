package com.auction.userservice.model;

import jakarta.validation.constraints.NotBlank;

public class DeregisterRequest {
    @NotBlank(message = "userId is required")
    private String userId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
