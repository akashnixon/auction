package com.auction.userservice.model;

import jakarta.validation.constraints.NotNull;

public class SellerStatusRequest {
    @NotNull(message = "isSelling is required")
    private Boolean isSelling;

    public Boolean getIsSelling() {
        return isSelling;
    }

    public void setIsSelling(Boolean isSelling) {
        this.isSelling = isSelling;
    }
}
