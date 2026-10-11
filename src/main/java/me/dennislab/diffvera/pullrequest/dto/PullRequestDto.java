package me.dennislab.diffvera.pullrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data transfer object representing a pull request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Pull request information")
public class PullRequestDto {

    @Schema(description = "Internal unique PR UUID", example = "c3d4e5f6-a7b8-9012-cdef-123456789012")
    private UUID id;

    @Schema(description = "Parent repository UUID", example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
    private UUID repositoryId;

    @Schema(description = "Numeric GitHub PR ID", example = "1098234")
    private Long githubPrId;

    @Schema(description = "Pull request number in repository", example = "42")
    private Integer number;

    @Schema(description = "Pull request title", example = "Add order details endpoint")
    private String title;

    @Schema(description = "Author GitHub login", example = "alex-morgan")
    private String author;

    @Schema(description = "Target base branch", example = "main")
    private String baseBranch;

    @Schema(description = "Head source branch", example = "feat/order-details")
    private String headBranch;

    @Schema(description = "Shorthand branch alias", example = "feat/order-details")
    private String branch;

    @Schema(description = "Target base commit SHA", example = "a1b2c3d4e5f67890a1b2c3d4e5f67890a1b2c3d4")
    private String baseSha;

    @Schema(description = "Head commit SHA under review", example = "f9b21a7123456789f9b21a7123456789f9b21a71")
    private String headSha;

    @Schema(description = "Status: open, merged, closed", example = "open")
    private String status;

    @Schema(description = "Number of additions", example = "34")
    private Integer additions;

    @Schema(description = "Number of deletions", example = "8")
    private Integer deletions;

    @Schema(description = "Creation timestamp")
    private OffsetDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private OffsetDateTime updatedAt;
}
