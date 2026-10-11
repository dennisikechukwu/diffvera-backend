package me.dennislab.diffvera.review;

import jakarta.persistence.*;
import lombok.*;
import me.dennislab.diffvera.rule.RuleVersion;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A concrete correctness, security, or architecture issue surfaced by the AI review.
 * Always anchored to an exact file path and line range.
 */
@Entity
@Table(name = "findings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Finding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Parent review run
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_run_id", nullable = false)
    private ReviewRun reviewRun;

    // Associated architecture rule version (nullable for security/correctness findings)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_version_id")
    private RuleVersion ruleVersion;

    // Category: "security", "architecture", "correctness"
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    // Severity level: "high", "medium", "low"
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    // Short headline of the issue
    @Column(name = "title", nullable = false, length = 300)
    private String title;

    // Plain English summary
    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    // Relative file path (e.g. "src/services/OrderService.ts")
    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    // 1-based start line number
    @Column(name = "line_start", nullable = false)
    private Integer lineStart;

    // 1-based end line number
    @Column(name = "line_end", nullable = false)
    private Integer lineEnd;

    // Read-only code excerpt around the finding
    @Column(name = "code_excerpt", nullable = false, columnDefinition = "TEXT")
    private String codeExcerpt;

    // Detailed explanation of why this is problematic
    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    // Suggested read-only replacement code
    @Column(name = "suggested_correction", columnDefinition = "TEXT")
    private String suggestedCorrection;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
