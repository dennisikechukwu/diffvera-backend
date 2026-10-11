package me.dennislab.diffvera.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Result payload returned upon processing a GitHub webhook.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Webhook processing acknowledgment")
public class WebhookResponseDto {

    @Schema(description = "Processing status: processed or ignored", example = "processed")
    private String status;

    @Schema(description = "The GitHub event type", example = "pull_request")
    private String event;

    @Schema(description = "The unique GitHub delivery ID", example = "72d5b6e0-1c23-11ec-9b8a-123456789abc")
    private String delivery;

    @Schema(description = "Human-readable summary of the action taken", example = "Queued review run for commit 7f3a9b1.")
    private String message;
}
