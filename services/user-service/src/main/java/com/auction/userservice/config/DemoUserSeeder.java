package com.auction.userservice.config;

import com.auction.userservice.model.User;
import com.auction.userservice.repository.UserRepository;
import com.auction.userservice.util.IdGenerator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

@Configuration
public class DemoUserSeeder {

    @Bean
    CommandLineRunner seedDemoUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            seedUser(userRepository, passwordEncoder, "john_doe", "john_doe@example.com", "password123");
            seedUser(userRepository, passwordEncoder, "jane_smith", "jane_smith@example.com", "securepass456");
        };
    }

    private void seedUser(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          String username, String email, String password) {
        userRepository.findByUsernameIgnoreCase(username).orElseGet(() -> {
            User user = new User();
            user.setId(IdGenerator.userIdFromUsername(username));
            user.setUsername(username);
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setActive(true);
            user.setSelling(false);
            user.setHighestBidder(false);
            user.setCreatedAt(Instant.now());
            return userRepository.save(user);
        });
    }
}
