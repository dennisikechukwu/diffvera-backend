package me.dennislab.diffvera.auth;

import me.dennislab.diffvera.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "a-very-strong-secret-key-that-has-over-256-bits-length-for-hmac-sha!";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, 3600);
    }

    @Test
    @DisplayName("Should generate valid JWT token and correctly extract user UUID")
    void shouldGenerateAndExtractUserId() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .githubUserId(123456L)
            .login("dennistest")
            .build();

        String token = jwtService.generateToken(user);
        assertNotNull(token);
        assertFalse(token.isBlank());

        Optional<UUID> extracted = jwtService.extractUserId(token);
        assertTrue(extracted.isPresent());
        assertEquals(userId, extracted.get());
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    @DisplayName("Should return empty optional when token is tampered or malformed")
    void shouldRejectTamperedToken() {
        String invalidToken = "eyJhbGciOiJIUzI1NiJ9.invalidpayload.invalidSignature";
        assertFalse(jwtService.isTokenValid(invalidToken));
        assertEquals(Optional.empty(), jwtService.extractUserId(invalidToken));
    }

    @Test
    @DisplayName("Should reject token when verified against a different secret")
    void shouldRejectTokenWithDifferentSecret() {
        JwtService anotherService = new JwtService("another-completely-different-super-secret-key-256-bits!", 3600);
        User user = User.builder()
            .id(UUID.randomUUID())
            .githubUserId(999L)
            .login("alice")
            .build();

        String token = jwtService.generateToken(user);
        assertFalse(anotherService.isTokenValid(token));
    }
}
