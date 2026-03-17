package com.aihiring.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {
    private JwtTokenProvider tokenProvider;
    private static final String SECRET = "test-secret-key-must-be-at-least-32-characters-long!";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 7200000, 604800000);
    }

    @Test
    void generateAccessToken_shouldCreateValidToken() {
        UUID userId = UUID.randomUUID();
        String username = "testuser";
        String token = tokenProvider.generateAccessToken(userId, username, java.util.Collections.emptyList());

        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    void validateToken_shouldReturnTrueForValidToken() {
        UUID userId = UUID.randomUUID();
        String username = "testuser";
        String token = tokenProvider.generateAccessToken(userId, username, java.util.Collections.emptyList());

        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void validateToken_shouldReturnFalseForExpiredToken() {
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(tokenProvider.validateToken(expiredToken));
    }
}
