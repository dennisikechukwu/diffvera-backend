package me.dennislab.diffvera.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request payload for manually triggering an AI review for a pull request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to trigger an AI review")
public class ReviewTriggerRequestDto {

    @NotNull(message = "pullRequestId is required")
    @Schema(description = "UUID of the pull request to review", example = "c3d4e5f6-a7b8-9012-cdef-123456789012")
    private UUID pullRequestId;
}
