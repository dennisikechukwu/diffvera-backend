package me.dennislab.diffvera.rule;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Data access for architecture rules.
 */
public interface ArchitectureRuleRepository extends JpaRepository<ArchitectureRule, UUID> {

    // List all rules for a given repository
    List<ArchitectureRule> findByRepositoryId(UUID repositoryId);

    // List only enabled rules for a given repository
    List<ArchitectureRule> findByRepositoryIdAndIsEnabledTrue(UUID repositoryId);

    // Count enabled rules for a given repository
    long countByRepositoryIdAndIsEnabledTrue(UUID repositoryId);

    // List all rules across multiple repositories
    List<ArchitectureRule> findByRepositoryIdIn(List<UUID> repositoryIds);
}
