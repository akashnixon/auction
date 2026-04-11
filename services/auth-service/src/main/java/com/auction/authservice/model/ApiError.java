package com.auction.authservice.model;

public class ApiError {
    private final String error;

    public ApiError(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
