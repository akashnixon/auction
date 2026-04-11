package com.auction.bidservice.models;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "bids",
    uniqueConstraints = @UniqueConstraint(name = "uk_bid_idempotency", columnNames = {"auction_id", "bidder_id", "idempotency_key"})
)
public class Bid {
    @Id
    @Column(name = "bid_id", nullable = false, updatable = false)
    private String bidId;

    @Column(name = "auction_id", nullable = false)
    private String auctionId;

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @Column(name = "bidder_id", nullable = false)
    private String bidderId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    public String getBidId() {
        return bidId;
    }

    public void setBidId(String bidId) {
        this.bidId = bidId;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public int getCycleNumber() {
        return cycleNumber;
    }

    public void setCycleNumber(int cycleNumber) {
        this.cycleNumber = cycleNumber;
    }

    public String getBidderId() {
        return bidderId;
    }

    public void setBidderId(String bidderId) {
        this.bidderId = bidderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
