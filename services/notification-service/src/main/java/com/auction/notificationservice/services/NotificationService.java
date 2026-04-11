package com.auction.notificationservice.services;

import com.auction.notificationservice.dto.BidMessageDTO;
import com.auction.notificationservice.models.AuctionEndMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // 🔥 Send bid update to all users in auction
    public void sendBidUpdate(BidMessageDTO bid) {
        messagingTemplate.convertAndSend(
                "/topic/auction/" + bid.getAuctionId(),
                bid
        );
    }

    // 🔥 Send auction end notification
    public void sendAuctionEnd(AuctionEndMessage result) {
        messagingTemplate.convertAndSend(
                "/topic/auction/" + result.getAuctionId(),
                result
        );
    }
}