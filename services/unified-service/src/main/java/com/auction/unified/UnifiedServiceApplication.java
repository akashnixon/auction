package com.auction.unified;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UnifiedServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UnifiedServiceApplication.class, args);
    }
}
