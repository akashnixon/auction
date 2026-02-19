package com.auction.userservice.model;

import jakarta.validation.constraints.NotNull;

public class BidderStatusRequest {
    @NotNull(message = "isHighestBidder is required")
    private Boolean isHighestBidder;

    public Boolean getIsHighestBidder() {
        return isHighestBidder;
    }

    public void setIsHighestBidder(Boolean isHighestBidder) {
        this.isHighestBidder = isHighestBidder;
    }
}
