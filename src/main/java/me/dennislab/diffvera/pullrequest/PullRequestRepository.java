package me.dennislab.diffvera.pullrequest;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for pull requests.
 */
public interface PullRequestRepository extends JpaRepository<PullRequest, UUID> {

    // Look up PR by repository and PR number (e.g. repo UUID + PR #42)
    Optional<PullRequest> findByRepositoryIdAndPrNumber(UUID repositoryId, Integer prNumber);

    // List all PRs in a repository
    List<PullRequest> findByRepositoryId(UUID repositoryId);

    // Filter PRs by status ("open", "merged", "closed")
    List<PullRequest> findByRepositoryIdAndStatus(UUID repositoryId, String status);

    // Find PR by head commit SHA
    Optional<PullRequest> findByHeadSha(String headSha);

    // Count PRs by repository and status
    long countByRepositoryIdAndStatus(UUID repositoryId, String status);

    // List all PRs across multiple repositories
    List<PullRequest> findByRepositoryIdIn(List<UUID> repositoryIds);
}
