package me.dennislab.diffvera.installation;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tracks an authorized installation of the Diffvera GitHub App on an account or organization.
 */
@Entity
@Table(name = "installations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Installation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Unique installation ID assigned by GitHub
    @Column(name = "github_installation_id", unique = true, nullable = false)
    private Long githubInstallationId;

    // Account or organization where the app was installed (e.g. "northstar")
    @Column(name = "account_login", nullable = false, length = 100)
    private String accountLogin;

    // Target type: "User" or "Organization"
    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    // Installation status: "connected", "pending_approval", "revoked"
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "connected";

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
