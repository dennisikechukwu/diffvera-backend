package me.dennislab.diffvera.pullrequest;

import jakarta.persistence.*;
import lombok.*;
import me.dennislab.diffvera.repository.Repository;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a pull request opened against a connected repository.
 */
@Entity
@Table(name = "pull_requests", uniqueConstraints = {
    @UniqueConstraint(name = "uq_repo_pr_number", columnNames = {"repository_id", "pr_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Parent repository
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    // Numeric ID from GitHub (e.g. 1347)
    @Column(name = "github_pr_id", nullable = false)
    private Long githubPrId;

    // PR number in the repo (e.g. #42)
    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    // GitHub username of the author who created the PR
    @Column(name = "author_login", nullable = false, length = 100)
    private String authorLogin;

    // Target branch (usually "main")
    @Column(name = "base_branch", nullable = false, length = 100)
    private String baseBranch;

    // Feature branch containing changes (e.g. "feat/refunds")
    @Column(name = "head_branch", nullable = false, length = 100)
    private String headBranch;

    // Base commit SHA on target branch
    @Column(name = "base_sha", nullable = false, length = 40)
    private String baseSha;

    // Head commit SHA being reviewed
    @Column(name = "head_sha", nullable = false, length = 40)
    private String headSha;

    // Status: "open", "merged", "closed"
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "open";

    @Builder.Default
    @Column(name = "additions", nullable = false)
    private Integer additions = 0;

    @Builder.Default
    @Column(name = "deletions", nullable = false)
    private Integer deletions = 0;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
