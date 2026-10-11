package me.dennislab.diffvera.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class WebhookSignatureVerifierTest {

    private final String secret = "super-secret-webhook-key-12345";
    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(secret);

    @Test
    @DisplayName("Should verify valid HMAC-SHA256 signature successfully")
    void shouldVerifyValidSignature() throws Exception {
        String payload = "{\"action\":\"opened\",\"number\":1}";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hex = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        String header = "sha256=" + hex;

        assertTrue(verifier.verify(payload, header));
    }

    @Test
    @DisplayName("Should reject tampered payload with valid header")
    void shouldRejectTamperedPayload() throws Exception {
        String originalPayload = "{\"action\":\"opened\"}";
        String tamperedPayload = "{\"action\":\"closed\"}";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hex = HexFormat.of().formatHex(mac.doFinal(originalPayload.getBytes(StandardCharsets.UTF_8)));
        String header = "sha256=" + hex;

        assertFalse(verifier.verify(tamperedPayload, header));
    }

    @Test
    @DisplayName("Should reject invalid or malformed header")
    void shouldRejectMalformedHeader() {
        assertFalse(verifier.verify("{}", null));
        assertFalse(verifier.verify("{}", ""));
        assertFalse(verifier.verify("{}", "invalid-signature"));
    }

    @Test
    @DisplayName("Should allow any signature when secret is unconfigured")
    void shouldPassWhenSecretUnconfigured() {
        WebhookSignatureVerifier unconfiguredVerifier = new WebhookSignatureVerifier("");
        assertFalse(unconfiguredVerifier.isSecretConfigured());
        assertTrue(unconfiguredVerifier.verify("{}", "any-value"));
    }
}
