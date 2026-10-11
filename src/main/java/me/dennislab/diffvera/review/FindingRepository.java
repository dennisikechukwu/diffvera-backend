package me.dennislab.diffvera.review;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Data access for review findings.
 */
public interface FindingRepository extends JpaRepository<Finding, UUID> {

    // List all findings produced by a review run
    List<Finding> findByReviewRunId(UUID reviewRunId);

    // Filter findings by review run and category ("security", "architecture", "correctness")
    List<Finding> findByReviewRunIdAndCategory(UUID reviewRunId, String category);

    // Filter findings by review run and severity ("high", "medium", "low")
    List<Finding> findByReviewRunIdAndSeverity(UUID reviewRunId, String severity);

    // Count findings by review run
    long countByReviewRunId(UUID reviewRunId);

    // Count findings by review run and severity
    long countByReviewRunIdAndSeverity(UUID reviewRunId, String severity);
}
