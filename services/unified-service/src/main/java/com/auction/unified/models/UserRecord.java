package com.auction.unified.models;

import java.util.HashSet;
import java.util.Set;

public class UserRecord {
    private String id;
    private String username;
    private String email;
    private boolean active;
    private String createdAt;
    private String deregisteredAt;
    private final Set<String> activeSellingAuctions = new HashSet<>();
    private final Set<String> activeLeadingAuctions = new HashSet<>();

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
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getDeregisteredAt() {
        return deregisteredAt;
    }

    public void setDeregisteredAt(String deregisteredAt) {
        this.deregisteredAt = deregisteredAt;
    }

    public Set<String> getActiveSellingAuctions() {
        return activeSellingAuctions;
    }

    public Set<String> getActiveLeadingAuctions() {
        return activeLeadingAuctions;
    }
}
