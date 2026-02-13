package com.auction.auctionservice.dto;

import java.util.Map;

public class NotificationEventRequest {
    private String type;
    private String audience;
    private String targetUserId;
    private Map<String, Object> payload;

    public NotificationEventRequest() {
    }

    public NotificationEventRequest(String type, String audience, String targetUserId, Map<String, Object> payload) {
        this.type = type;
        this.audience = audience;
        this.targetUserId = targetUserId;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
