package com.auction.auctionservice.dto;

public class CreateAuctionRequest {
    private String itemName;
    private String sellerId;
    private String imageDataUrl;
    private Double startingPrice;

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

    public Double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(Double startingPrice) {
        this.startingPrice = startingPrice;
    }
}
