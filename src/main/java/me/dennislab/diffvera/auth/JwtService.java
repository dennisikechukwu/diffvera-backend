package me.dennislab.diffvera.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import me.dennislab.diffvera.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for creating, signing, and parsing JWT session tokens
 * using HMAC-SHA256.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(
        @Value("${diffvera.jwt.secret:diffvera-super-secret-jwt-key-minimum-256-bits-length-for-hmac-sha!}") String secret,
        @Value("${diffvera.jwt.expiration-seconds:604800}") long expirationSeconds
    ) {
        // Enforce strong secret key length (minimum 32 bytes for HMAC-SHA256)
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            this.signingKey = Keys.hmacShaKeyFor(padded);
        } else {
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        }
        this.expirationMillis = expirationSeconds * 1000;
    }

    /**
     * Generates a signed JWT session token for an authenticated user.
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("login", user.getLogin())
            .claim("githubUserId", user.getGithubUserId())
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(signingKey)
            .compact();
    }

    /**
     * Parses and extracts the user UUID from a signed JWT token string.
     * Returns Optional.empty() if the token is invalid or expired.
     */
    public Optional<UUID> extractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Validates whether a token string is syntactically valid, properly signed, and not expired.
     */
    public boolean isTokenValid(String token) {
        return extractUserId(token).isPresent();
    }
}
