package com.auction.unified.dto;

public class UpdateBidderStatusRequest {
    private Boolean isHighestBidder;
    private String auctionId;

    public Boolean getIsHighestBidder() {
        return isHighestBidder;
    }

    public void setIsHighestBidder(Boolean isHighestBidder) {
        this.isHighestBidder = isHighestBidder;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }
}
