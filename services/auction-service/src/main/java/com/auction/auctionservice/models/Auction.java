package com.auction.auctionservice.models;

import java.time.Instant;
import java.util.UUID;

public class Auction {

    private UUID auctionId;
    private String itemName;
    private String sellerId;

    private Instant startTime;
    private Instant endTime;

    private AuctionStatus status;
    private String winningBidId;

    public Auction() {}

    public Auction(String itemName, String sellerId) {
        this.auctionId = UUID.randomUUID();
        this.itemName = itemName;
        this.sellerId = sellerId;
        this.startTime = Instant.now();
        this.endTime = startTime.plusSeconds(300); // 5 minutes
        this.status = AuctionStatus.ACTIVE;
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(UUID auctionId) {
        this.auctionId = auctionId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public String getWinningBidId() {
        return winningBidId;
    }

    public void setWinningBidId(String winningBidId) {
        this.winningBidId = winningBidId;
    }
}
