package me.dennislab.diffvera.review;

import jakarta.persistence.*;
import lombok.*;
import me.dennislab.diffvera.pullrequest.PullRequest;
import me.dennislab.diffvera.rule.RuleVersion;
import me.dennislab.diffvera.user.User;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An execution of an AI code review on a specific pull request commit.
 * Tracks lifecycle status: "queued" -> "running" -> "completed" or "failed".
 */
@Entity
@Table(name = "review_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Target pull request
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pull_request_id", nullable = false)
    private PullRequest pullRequest;

    // The commit SHA reviewed (e.g. 7f3a9b1...)
    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    // Review status: "queued", "running", "completed", "failed"
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "queued";

    // Explanatory message if status is "failed"
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    // User who manually clicked "Review" (nullable if triggered via webhook)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedByUser;

    @Builder.Default
    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    // The immutable rule versions active and evaluated during this specific review run
    @Builder.Default
    @ManyToMany
    @JoinTable(
        name = "review_rule_snapshots",
        joinColumns = @JoinColumn(name = "review_run_id"),
        inverseJoinColumns = @JoinColumn(name = "rule_version_id")
    )
    private Set<RuleVersion> ruleVersions = new HashSet<>();
}
