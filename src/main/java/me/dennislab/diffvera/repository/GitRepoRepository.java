package me.dennislab.diffvera.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for connected Git repositories.
 */
public interface GitRepoRepository extends JpaRepository<Repository, UUID> {

    // Look up repository by its GitHub numeric ID
    Optional<Repository> findByGithubRepoId(Long githubRepoId);

    // Look up repository by full name (e.g. "northstar/order-service")
    Optional<Repository> findByFullName(String fullName);

    // List all repositories associated with an installation
    List<Repository> findByInstallationId(UUID installationId);

    // List all repositories by access status (e.g. "available")
    List<Repository> findByAccessStatus(String accessStatus);

    // List repositories accessible to a specific user/account, sorted newest first
    @org.springframework.data.jpa.repository.Query("SELECT r FROM Repository r WHERE LOWER(r.installation.accountLogin) = LOWER(:login) OR LOWER(r.fullName) LIKE LOWER(CONCAT(:login, '/%')) ORDER BY COALESCE(r.pushedAt, r.updatedAt, r.createdAt) DESC, r.createdAt DESC")
    List<Repository> findAccessibleRepositories(@org.springframework.data.repository.query.Param("login") String login);

    // List all repositories ordered newest first
    @org.springframework.data.jpa.repository.Query("SELECT r FROM Repository r ORDER BY COALESCE(r.pushedAt, r.updatedAt, r.createdAt) DESC, r.createdAt DESC")
    List<Repository> findAllNewestFirst();

    // Count accessible repositories
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(r) FROM Repository r WHERE LOWER(r.installation.accountLogin) = LOWER(:login) OR LOWER(r.fullName) LIKE LOWER(CONCAT(:login, '/%'))")
    long countAccessibleRepositories(@org.springframework.data.repository.query.Param("login") String login);
}
