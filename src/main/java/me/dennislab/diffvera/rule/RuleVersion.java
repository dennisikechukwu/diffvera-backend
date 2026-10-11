package me.dennislab.diffvera.rule;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An immutable version snapshot of an architecture rule.
 * Guarantees that historical reviews never rewrite past findings when rules change.
 */
@Entity
@Table(name = "rule_versions", uniqueConstraints = {
    @UniqueConstraint(name = "uq_rule_version", columnNames = {"rule_id", "version_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Parent rule
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private ArchitectureRule rule;

    // Incremental version number (1, 2, 3...)
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    // Snapshot name of the rule at this version
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    // Natural-language architectural boundary text evaluated by the AI
    @Column(name = "boundary", nullable = false, columnDefinition = "TEXT")
    private String boundary;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
