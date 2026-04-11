package com.auction.authservice.model;

import jakarta.validation.constraints.NotBlank;

public class ServiceTokenRequest {
    @NotBlank(message = "serviceName is required")
    private String serviceName;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}