package me.dennislab.diffvera.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.dennislab.diffvera.review.dto.FindingResponseDto;
import me.dennislab.diffvera.review.dto.ReviewFileDiffDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated workspace snapshot perfectly matching the Diffvera frontend contract.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Aggregated workspace state snapshot")
public class WorkspaceSnapshotDto {

    @Schema(description = "Connected GitHub account information")
    private WorkspaceAccountDto account;

    @Builder.Default
    @Schema(description = "Connected repositories")
    private List<WorkspaceRepositoryDto> repositories = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Open and recent pull requests")
    private List<WorkspacePullRequestDto> pullRequests = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Completed and in-flight review runs")
    private List<WorkspaceReviewDto> reviews = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Active architecture governance rules")
    private List<WorkspaceRuleDto> rules = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Developer identity information")
    public static class WorkspaceAccountDto {
        private String id;
        private String name;
        private String login;
        private String avatarUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Repository in workspace snapshot")
    public static class WorkspaceRepositoryDto {
        private String id;
        private String fullName;
        private String language;
        private String visibility;
        private String description;
        private String access;
        private String defaultBranch;
        private String updatedAt;
        private String pushedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Pull request in workspace snapshot")
    public static class WorkspacePullRequestDto {
        private String id;
        private String repositoryId;
        private int number;
        private String title;
        private String author;
        private String status;
        private String headSha;
        private String branch;
        private String updatedAt;
        private int additions;
        private int deletions;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Review in workspace snapshot")
    public static class WorkspaceReviewDto {
        private String id;
        private String pullRequestId;
        private String headSha;
        private String status;
        private String createdAt;
        private Long requestedAt;
        @Builder.Default
        private List<FindingResponseDto> findings = new ArrayList<>();
        @Builder.Default
        private List<ReviewFileDiffDto> files = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Architecture rule in workspace snapshot")
    public static class WorkspaceRuleDto {
        private String id;
        private String name;
        private String description;
        private String repositoryId;
        private boolean enabled;
    }
}
