package me.dennislab.diffvera.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Result payload returned upon triggering a repository re-sync.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Repository synchronization result")
public class RepositorySyncResultDto {

    @Schema(description = "Sync status: completed or failed", example = "completed")
    private String status;

    @Schema(description = "Total number of repositories synchronized", example = "12")
    private int syncedRepositoriesCount;

    @Schema(description = "Summary message", example = "Synchronized 12 repositories across 1 active installation.")
    private String message;
}
