package me.dennislab.diffvera.repository;

import jakarta.persistence.*;
import lombok.*;
import me.dennislab.diffvera.installation.Installation;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A Git repository connected to Diffvera via an active GitHub App installation.
 */
@Entity
@Table(name = "repositories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The GitHub App installation providing access to this repository
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installation_id", nullable = false)
    private Installation installation;

    // Numeric ID from GitHub (e.g. 1296269)
    @Column(name = "github_repo_id", unique = true, nullable = false)
    private Long githubRepoId;

    // Short repository name (e.g. "order-service")
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    // Full name with owner (e.g. "northstar/order-service")
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Builder.Default
    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate = false;

    // Primary detected language (e.g. "TypeScript", "Python", "Go", "Java")
    @Column(name = "language", length = 50)
    private String language;

    // Default target branch (e.g. "main")
    @Builder.Default
    @Column(name = "default_branch", nullable = false, length = 100)
    private String defaultBranch = "main";

    // Access status: "available" or "revoked"
    @Builder.Default
    @Column(name = "access_status", nullable = false, length = 30)
    private String accessStatus = "available";

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    // Latest commit push timestamp on GitHub
    @Column(name = "pushed_at")
    private OffsetDateTime pushedAt;

    @PrePersist
    public void onPersist() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = OffsetDateTime.now();
        }
    }
}
