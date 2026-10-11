package me.dennislab.diffvera.rule;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for immutable rule versions.
 */
public interface RuleVersionRepository extends JpaRepository<RuleVersion, UUID> {

    // Find specific version of a rule
    Optional<RuleVersion> findByRuleIdAndVersionNumber(UUID ruleId, Integer versionNumber);

    // List all historical versions of a rule ordered by version number descending
    List<RuleVersion> findByRuleIdOrderByVersionNumberDesc(UUID ruleId);
}
