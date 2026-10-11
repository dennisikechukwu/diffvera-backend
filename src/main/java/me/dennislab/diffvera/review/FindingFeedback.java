package me.dennislab.diffvera.review;

import jakarta.persistence.*;
import lombok.*;
import me.dennislab.diffvera.user.User;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Developer assessment on an individual finding.
 * Mutually exclusive values: "useful", "false_positive", "resolved".
 */
@Entity
@Table(name = "finding_feedback", uniqueConstraints = {
    @UniqueConstraint(name = "uq_finding_user_feedback", columnNames = {"finding_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindingFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // The finding being assessed
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finding_id", nullable = false)
    private Finding finding;

    // The developer who submitted the feedback
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Assessment: "useful", "false_positive", "resolved"
    @Column(name = "feedback", nullable = false, length = 30)
    private String feedback;

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
