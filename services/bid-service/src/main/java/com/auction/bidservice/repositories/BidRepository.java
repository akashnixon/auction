package com.auction.bidservice.repositories;

import com.auction.bidservice.models.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, String> {
    List<Bid> findByAuctionIdOrderByReceivedAtAsc(String auctionId);

    Optional<Bid> findByAuctionIdAndBidderIdAndIdempotencyKey(String auctionId, String bidderId, String idempotencyKey);

    @Query("""
        SELECT b FROM Bid b
        WHERE b.auctionId = :auctionId AND b.cycleNumber = :cycleNumber
        ORDER BY b.amount DESC, b.receivedAt ASC, b.bidId ASC
    """)
    List<Bid> findHighestForCycleOrdered(@Param("auctionId") String auctionId, @Param("cycleNumber") int cycleNumber);
}
