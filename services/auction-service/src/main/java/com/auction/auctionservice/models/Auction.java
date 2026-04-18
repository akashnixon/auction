package com.auction.auctionservice.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "auctions")
public class Auction {

    @Id
    @Column(name = "auction_id", nullable = false, updatable = false)
    private String auctionId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Lob
    @Column(name = "image_data_url")
    private String imageDataUrl;

    @Column(name = "starting_price", nullable = false)
    private BigDecimal startingPrice;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AuctionStatus status;

    @Column(name = "winning_bid_id")
    private String winningBidId;

    @Column(name = "winner_user_id")
    private String winnerUserId;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
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

    public String getImageDataUrl() {
        return imageDataUrl;
    }

    public void setImageDataUrl(String imageDataUrl) {
        this.imageDataUrl = imageDataUrl;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
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

    public int getCycleNumber() {
        return cycleNumber;
    }

    public void setCycleNumber(int cycleNumber) {
        this.cycleNumber = cycleNumber;
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

    public String getWinnerUserId() {
        return winnerUserId;
    }

    public void setWinnerUserId(String winnerUserId) {
        this.winnerUserId = winnerUserId;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(Instant finalizedAt) {
        this.finalizedAt = finalizedAt;
    }
}
