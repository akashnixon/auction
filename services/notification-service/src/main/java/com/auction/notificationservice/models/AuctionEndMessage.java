package com.auction.notificationservice.models;

public class AuctionEndMessage {
        private String auctionId;
        private String winnerName;
        private double finalPrice;

        public String getAuctionId() { return auctionId; }
        public void setAuctionId(String auctionId) { this.auctionId = auctionId; }

        public String getWinnerName() { return winnerName; }
        public void setWinnerName(String winnerName) { this.winnerName = winnerName; }

        public double getFinalPrice() { return finalPrice; }
        public void setFinalPrice(double finalPrice) { this.finalPrice = finalPrice; }
    }

