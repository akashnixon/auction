package com.auction.notificationservice.controllers;

import com.auction.notificationservice.dto.BidMessageDTO;
import com.auction.notificationservice.models.AuctionEndMessage;
import com.auction.notificationservice.services.NotificationService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // 📡 Receive bid from frontend
    @MessageMapping("/bid")
    public void handleBid(BidMessageDTO bid) {
        notificationService.sendBidUpdate(bid);
    }

    // 🏁 Auction ended
    @MessageMapping("/auctionEnd")
    public void handleAuctionEnd(AuctionEndMessage result) {
        notificationService.sendAuctionEnd(result);
    }
}