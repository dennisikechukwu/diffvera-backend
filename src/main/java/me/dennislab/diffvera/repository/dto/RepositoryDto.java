package me.dennislab.diffvera.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Data transfer object representing a connected repository with governance metrics.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Connected Git repository information")
public class RepositoryDto {

    @Schema(description = "Internal unique repository UUID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID id;

    @Schema(description = "Numeric GitHub repository ID", example = "1296269")
    private Long githubRepoId;

    @Schema(description = "Short repository name", example = "order-service")
    private String name;

    @Schema(description = "Full name with organization or owner", example = "northstar/order-service")
    private String fullName;

    @Schema(description = "Whether the repository is private", example = "false")
    private Boolean isPrivate;

    @Schema(description = "Primary detected programming language", example = "TypeScript")
    private String language;

    @Schema(description = "Default branch", example = "main")
    private String defaultBranch;

    @Schema(description = "Access status: available or revoked", example = "available")
    private String accessStatus;

    @Schema(description = "Total active architecture governance rules", example = "4")
    private long activeRuleCount;

    @Schema(description = "Number of open pull requests", example = "2")
    private long openPrCount;

    @Schema(description = "Last update timestamp")
    private OffsetDateTime updatedAt;

    @Schema(description = "Last commit push timestamp")
    private OffsetDateTime pushedAt;
}
