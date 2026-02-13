package com.auction.unified.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupVisibilityLogger implements ApplicationRunner {

    @Value("${server.port:8080}")
    private String port;

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("[UNIFIED] User Service initialized on /users (port " + port + ")");
        System.out.println("[UNIFIED] Auth Service initialized on /auth (port " + port + ")");
        System.out.println("[UNIFIED] Auction Service initialized on /auctions (port " + port + ")");
        System.out.println("[UNIFIED] Bid Service initialized on /bids (port " + port + ")");
        System.out.println("[UNIFIED] Notification Service initialized on /events and /stream (port " + port + ")");
    }
}
