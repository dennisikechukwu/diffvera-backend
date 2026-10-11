package me.dennislab.diffvera.review;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for review runs.
 */
public interface ReviewRunRepository extends JpaRepository<ReviewRun, UUID> {

    // Find review run with pull request, repository, and installation eagerly fetched
    @org.springframework.data.jpa.repository.Query("SELECT r FROM ReviewRun r JOIN FETCH r.pullRequest p JOIN FETCH p.repository repo LEFT JOIN FETCH repo.installation WHERE r.id = :id")
    Optional<ReviewRun> findByIdWithDetails(@org.springframework.data.repository.query.Param("id") UUID id);

    // List all review runs for a pull request, newest first
    List<ReviewRun> findByPullRequestIdOrderByRequestedAtDesc(UUID pullRequestId);

    // Find any existing active run (queued or running) on a specific PR commit
    Optional<ReviewRun> findByPullRequestIdAndCommitShaAndStatusIn(UUID pullRequestId, String commitSha, List<String> statuses);

    // List reviews by status (e.g. "queued", "running", "completed", "failed")
    List<ReviewRun> findByStatus(String status);

    // List all review runs ordered by newest first
    List<ReviewRun> findAllByOrderByRequestedAtDesc();

    // List reviews by repository ordered by newest first
    List<ReviewRun> findByPullRequestRepositoryIdOrderByRequestedAtDesc(UUID repositoryId);

    // List reviews across multiple repositories ordered by newest first
    List<ReviewRun> findByPullRequestRepositoryIdInOrderByRequestedAtDesc(List<UUID> repositoryIds);

    // List reviews by repository and status ordered by newest first
    List<ReviewRun> findByPullRequestRepositoryIdAndStatusOrderByRequestedAtDesc(UUID repositoryId, String status);

    // List reviews by status ordered by newest first
    List<ReviewRun> findByStatusOrderByRequestedAtDesc(String status);
}
