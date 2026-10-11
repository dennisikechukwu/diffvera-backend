package me.dennislab.diffvera.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Validates GitHub X-Hub-Signature-256 headers using HMAC-SHA256
 * and constant-time comparison to prevent timing attacks.
 */
@Component
@Slf4j
public class WebhookSignatureVerifier {

    private final String webhookSecret;

    public WebhookSignatureVerifier(
        @Value("${diffvera.github.webhook-secret:}") String webhookSecret
    ) {
        this.webhookSecret = webhookSecret != null ? webhookSecret.trim() : "";
    }

    /**
     * Checks if a webhook secret is configured.
     */
    public boolean isSecretConfigured() {
        return !webhookSecret.isBlank();
    }

    /**
     * Validates the GitHub signature header against the raw request body.
     */
    public boolean verify(String rawBody, String signatureHeader) {
        if (!isSecretConfigured()) {
            log.debug("Webhook secret is not configured; skipping HMAC verification.");
            return true;
        }

        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("Missing or malformed X-Hub-Signature-256 header: {}", signatureHeader);
            return false;
        }

        if (rawBody == null) {
            return false;
        }

        try {
            String receivedHash = signatureHeader.substring("sha256=".length()).trim();

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);

            byte[] hmacBytes = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String expectedHash = HexFormat.of().formatHex(hmacBytes);

            return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                receivedHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error calculating HMAC-SHA256 for webhook payload: {}", e.getMessage(), e);
            return false;
        }
    }
}
