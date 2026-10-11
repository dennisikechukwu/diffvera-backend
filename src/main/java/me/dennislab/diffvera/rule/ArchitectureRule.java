package me.dennislab.diffvera.rule;

import jakarta.persistence.*;
import lombok.*;
import me.dennislab.diffvera.repository.Repository;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An architectural governance rule configured for a repository.
 * Historical review runs link to immutable RuleVersion snapshots.
 */
@Entity
@Table(name = "architecture_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchitectureRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The repository this architectural rule applies to
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    // Human-readable rule title (e.g. "Controllers must not import Services directly")
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    // Whether this rule is currently enabled for future reviews
    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    // Pointer to current active version number
    @Builder.Default
    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 1;

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
