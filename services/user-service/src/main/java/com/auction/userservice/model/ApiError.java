package com.auction.userservice.model;

public class ApiError {
    private final String error;
    private final String reason;

    public ApiError(String error) {
        this(error, null);
    }

    public ApiError(String error, String reason) {
        this.error = error;
        this.reason = reason;
    }

    public String getError() {
        return error;
    }

    public String getReason() {
        return reason;
    }
}
