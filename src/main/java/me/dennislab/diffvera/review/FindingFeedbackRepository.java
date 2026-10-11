package me.dennislab.diffvera.review;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for developer feedback on findings.
 */
public interface FindingFeedbackRepository extends JpaRepository<FindingFeedback, UUID> {

    // Find existing feedback by finding and user
    Optional<FindingFeedback> findByFindingIdAndUserId(UUID findingId, UUID userId);

    // Find any feedback submitted on a finding
    Optional<FindingFeedback> findByFindingId(UUID findingId);
}
