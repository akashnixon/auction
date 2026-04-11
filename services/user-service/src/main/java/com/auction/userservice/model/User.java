package com.auction.userservice.model;

import java.time.Instant;

public class User {
    private String id;
    private String username;
    private String email;
    private boolean isActive;
    private boolean isSelling;
    private boolean isHighestBidder;
    private Instant createdAt;
    private Instant deregisteredAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isSelling() {
        return isSelling;
    }

    public void setSelling(boolean selling) {
        isSelling = selling;
    }

    public boolean isHighestBidder() {
        return isHighestBidder;
    }

    public void setHighestBidder(boolean highestBidder) {
        isHighestBidder = highestBidder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDeregisteredAt() {
        return deregisteredAt;
    }

    public void setDeregisteredAt(Instant deregisteredAt) {
        this.deregisteredAt = deregisteredAt;
    }
}
