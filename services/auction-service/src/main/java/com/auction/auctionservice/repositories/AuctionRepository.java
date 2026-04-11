package com.auction.auctionservice.repositories;

import com.auction.auctionservice.models.Auction;
import com.auction.auctionservice.models.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction, String> {
    List<Auction> findByStatusOrderByStartTimeDesc(AuctionStatus status);

    List<Auction> findByStatusAndEndTimeLessThanEqual(AuctionStatus status, Instant endTime);

    List<Auction> findBySellerIdAndStatus(String sellerId, AuctionStatus status);
}
