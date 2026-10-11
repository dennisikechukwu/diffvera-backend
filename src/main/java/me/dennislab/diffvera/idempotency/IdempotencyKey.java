package me.dennislab.diffvera.idempotency;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

/**
 * Tracks processed delivery IDs (e.g. X-GitHub-Delivery) to guarantee idempotency.
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    // Unique key (e.g. webhook delivery UUID)
    @Id
    @Column(name = "key", length = 255)
    private String key;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
