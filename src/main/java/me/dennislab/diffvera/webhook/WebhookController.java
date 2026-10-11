package me.dennislab.diffvera.webhook;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.common.error.UnauthorizedException;
import me.dennislab.diffvera.idempotency.IdempotencyService;
import me.dennislab.diffvera.webhook.dto.WebhookResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for receiving and processing GitHub App webhooks.
 * Enforces HMAC-SHA256 signature verification and delivery idempotency.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "GitHub App webhook receiver and event processing")
@Slf4j
public class WebhookController {

    private final WebhookSignatureVerifier signatureVerifier;
    private final IdempotencyService idempotencyService;
    private final WebhookDispatcherService dispatcherService;

    public WebhookController(
        WebhookSignatureVerifier signatureVerifier,
        IdempotencyService idempotencyService,
        WebhookDispatcherService dispatcherService
    ) {
        this.signatureVerifier = signatureVerifier;
        this.idempotencyService = idempotencyService;
        this.dispatcherService = dispatcherService;
    }

    @Operation(summary = "GitHub Webhook Receiver", description = "Ingests real-time pull request, installation, and ping events from GitHub.")
    @ApiResponse(responseCode = "200", description = "Webhook received and processed or deduplicated",
        content = @Content(schema = @Schema(implementation = WebhookResponseDto.class)))
    @ApiResponse(responseCode = "401", description = "Invalid or missing HMAC SHA-256 webhook signature")
    @PostMapping(value = "/github", consumes = "application/json")
    public ResponseEntity<WebhookResponseDto> handleGitHubWebhook(
        @Parameter(description = "GitHub event type") @RequestHeader(value = "X-GitHub-Event", defaultValue = "ping") String event,
        @Parameter(description = "Unique delivery UUID") @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
        @Parameter(description = "HMAC-SHA256 signature") @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
        @RequestBody String rawPayload
    ) {
        String effectiveDelivery = deliveryId != null ? deliveryId : "local-delivery";

        // 1. Verify HMAC-SHA256 signature
        if (!signatureVerifier.verify(rawPayload, signature)) {
            log.warn("Rejected webhook request with invalid HMAC signature on delivery {}", effectiveDelivery);
            throw new UnauthorizedException("Invalid X-Hub-Signature-256 header. Signature verification failed.");
        }

        // 2. Enforce idempotency: prevent re-processing identical delivery UUID
        if (deliveryId != null && !idempotencyService.tryAcquire(deliveryId)) {
            log.info("Duplicate delivery {} detected for event {}; dropping duplicate.", deliveryId, event);
            return ResponseEntity.ok(WebhookResponseDto.builder()
                .status("ignored")
                .event(event)
                .delivery(deliveryId)
                .message("Duplicate delivery dropped by idempotency guard.")
                .build());
        }

        // 3. Dispatch and process event
        WebhookResponseDto result = dispatcherService.dispatch(event, effectiveDelivery, rawPayload);
        return ResponseEntity.ok(result);
    }
}
