package com.auction.auctionservice.schedulers;

import com.auction.auctionservice.models.Auction;
import com.auction.auctionservice.services.AuctionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuctionScheduler {

    private final AuctionService auctionService;

    @Value("${auction.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${auction.scheduler.leader:true}")
    private boolean leader;

    public AuctionScheduler(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @Scheduled(fixedRate = 1000)
    public void checkAuctions() {
        if (!schedulerEnabled || !leader) {
            return;
        }

        List<Auction> expired = auctionService.findExpiredActiveAuctions();
        for (Auction auction : expired) {
            auctionService.processExpiredAuction(auction);
        }
    }
}
