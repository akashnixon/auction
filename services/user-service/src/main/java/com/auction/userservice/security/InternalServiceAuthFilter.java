package com.auction.userservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalServiceAuthFilter extends OncePerRequestFilter {
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String SERVICE_NAME_HEADER = "X-Service-Name";

    private final JwtService jwtService;
    private final String internalServiceSecret;

    public InternalServiceAuthFilter(JwtService jwtService,
                                     @Value("${internal.service.secret}") String internalServiceSecret) {
        this.jwtService = jwtService;
        this.internalServiceSecret = internalServiceSecret;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.matches("^/users/[^/]+/update-seller-status$") || path.matches("^/users/[^/]+/update-bidder-status$"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String internalAuthHeader = request.getHeader(INTERNAL_AUTH_HEADER);
        String serviceNameHeader = request.getHeader(SERVICE_NAME_HEADER);

        if (!safeEquals(internalAuthHeader, internalServiceSecret)) {
            writeUnauthorized(response, "Invalid internal authentication secret");
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing bearer token");
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.parseClaims(token);
            String role = claims.get("role", String.class);
            String tokenServiceName = claims.get("serviceName", String.class);

            if (!"SERVICE".equals(role)) {
                writeForbidden(response, "Token role is not allowed for internal endpoint");
                return;
            }

            if (serviceNameHeader == null || serviceNameHeader.isBlank()) {
                writeForbidden(response, "Missing service name header");
                return;
            }

            if (tokenServiceName == null || !serviceNameHeader.equalsIgnoreCase(tokenServiceName)) {
                writeForbidden(response, "Service identity mismatch");
                return;
            }

            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            writeUnauthorized(response, "Invalid token");
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private boolean safeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}