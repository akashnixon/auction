package com.auction.unified.store;

import com.auction.unified.models.AuctionRecord;
import com.auction.unified.models.BidRecord;
import com.auction.unified.models.NotificationEvent;
import com.auction.unified.models.UserRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryStore {
    public final Map<String, UserRecord> users = new ConcurrentHashMap<>();
    public final Map<String, String> credentialPasswords = new ConcurrentHashMap<>();
    public final Map<String, AuctionRecord> auctions = new ConcurrentHashMap<>();
    public final Map<String, List<BidRecord>> bidsByAuction = new ConcurrentHashMap<>();
    public final Map<String, BidRecord> highestByAuctionCycle = new ConcurrentHashMap<>();
    public final Map<String, Map<String, Object>> processedIdempotency = new ConcurrentHashMap<>();
    public final Map<String, SseEmitter> sseClients = new ConcurrentHashMap<>();
    public final Map<String, String> sseClientUserIds = new ConcurrentHashMap<>();
    public final List<NotificationEvent> eventHistory = new ArrayList<>();
}
