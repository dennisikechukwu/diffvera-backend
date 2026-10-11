package me.dennislab.diffvera.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Detailed view of a review run with all findings and changed file diffs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Detailed review run representation with findings and diff lines")
public class ReviewDetailDto {

    @Schema(description = "Unique review UUID", example = "e5f6a7b8-c9d0-1234-ef01-234567890123")
    private UUID id;

    @Schema(description = "Target pull request UUID")
    private UUID pullRequestId;

    @Schema(description = "Pull request number", example = "42")
    private Integer pullRequestNumber;

    @Schema(description = "Pull request title", example = "Add order details endpoint")
    private String pullRequestTitle;

    @Schema(description = "Repository full name", example = "northstar/order-service")
    private String repositoryFullName;

    @Schema(description = "Reviewed commit SHA", example = "f9b21a7123456789f9b21a7123456789f9b21a71")
    private String commitSha;

    @Schema(description = "Head commit SHA alias", example = "f9b21a7123456789f9b21a7123456789f9b21a71")
    private String headSha;

    @Schema(description = "Status: queued, running, completed, failed", example = "completed")
    private String status;

    @Schema(description = "Failure explanation if failed")
    private String failureReason;

    @Schema(description = "Timestamp when review was created or requested")
    private OffsetDateTime createdAt;

    @Schema(description = "Requested timestamp")
    private OffsetDateTime requestedAt;

    @Schema(description = "Started execution timestamp")
    private OffsetDateTime startedAt;

    @Schema(description = "Completed execution timestamp")
    private OffsetDateTime completedAt;

    @Builder.Default
    @Schema(description = "List of findings surfaced by the review")
    private List<FindingResponseDto> findings = new ArrayList<>();

    @Builder.Default
    @Schema(description = "List of changed files with line-by-line diff content")
    private List<ReviewFileDiffDto> files = new ArrayList<>();
}
