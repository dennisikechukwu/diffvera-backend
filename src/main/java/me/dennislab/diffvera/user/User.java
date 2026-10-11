package me.dennislab.diffvera.user;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a registered developer authenticated via GitHub OAuth.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Unique numeric ID from GitHub (e.g. 583231)
    @Column(name = "github_user_id", unique = true, nullable = false)
    private Long githubUserId;

    // GitHub username handle (e.g. "octocat")
    @Column(name = "login", nullable = false, length = 100)
    private String login;

    // Developer display name (e.g. "Mona Lisa")
    @Column(name = "name")
    private String name;

    @Column(name = "email")
    private String email;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

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
