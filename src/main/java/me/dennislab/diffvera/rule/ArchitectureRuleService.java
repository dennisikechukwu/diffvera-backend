package me.dennislab.diffvera.rule;

import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.common.error.ResourceNotFoundException;
import me.dennislab.diffvera.repository.GitRepoRepository;
import me.dennislab.diffvera.repository.Repository;
import me.dennislab.diffvera.rule.dto.CreateRuleRequestDto;
import me.dennislab.diffvera.rule.dto.RuleResponseDto;
import me.dennislab.diffvera.rule.dto.RuleVersionDto;
import me.dennislab.diffvera.rule.dto.UpdateRuleRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing architecture governance rules with immutable version snapshots.
 */
@Service
@Slf4j
public class ArchitectureRuleService {

    private final ArchitectureRuleRepository ruleRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final GitRepoRepository gitRepoRepository;

    public ArchitectureRuleService(
        ArchitectureRuleRepository ruleRepository,
        RuleVersionRepository ruleVersionRepository,
        GitRepoRepository gitRepoRepository
    ) {
        this.ruleRepository = ruleRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.gitRepoRepository = gitRepoRepository;
    }

    /**
     * Lists all rules configured for a repository, including their current active boundary.
     */
    @Transactional(readOnly = true)
    public List<RuleResponseDto> getRulesByRepository(UUID repositoryId) {
        List<ArchitectureRule> rules = ruleRepository.findByRepositoryId(repositoryId);
        return rules.stream()
            .map(this::toResponseDto)
            .toList();
    }

    /**
     * Retrieves an individual architecture rule by its unique ID.
     */
    @Transactional(readOnly = true)
    public RuleResponseDto getRuleById(UUID ruleId) {
        ArchitectureRule rule = findRuleOrThrow(ruleId);
        return toResponseDto(rule);
    }

    /**
     * Creates a new architectural rule and its initial immutable Version 1 snapshot.
     */
    @Transactional
    public RuleResponseDto createRule(UUID repositoryId, CreateRuleRequestDto request) {
        Repository repository = gitRepoRepository.findById(repositoryId)
            .orElseThrow(() -> new ResourceNotFoundException("Repository not found with id: " + repositoryId));

        ArchitectureRule rule = ArchitectureRule.builder()
            .repository(repository)
            .name(request.getName().trim())
            .isEnabled(true)
            .currentVersion(1)
            .build();

        ArchitectureRule savedRule = ruleRepository.save(rule);

        RuleVersion initialVersion = RuleVersion.builder()
            .rule(savedRule)
            .versionNumber(1)
            .name(savedRule.getName())
            .boundary(request.getBoundary().trim())
            .build();

        ruleVersionRepository.save(initialVersion);
        log.info("Created new ArchitectureRule '{}' (id: {}) with version 1 for repository {}",
            savedRule.getName(), savedRule.getId(), repository.getFullName());

        return toResponseDto(savedRule, initialVersion.getBoundary());
    }

    /**
     * Updates an architecture rule.
     * Enforces immutable versioning: if either the name or boundary constraint changes,
     * a new immutable RuleVersion (version N+1) is recorded to preserve historical review integrity.
     */
    @Transactional
    public RuleResponseDto updateRule(UUID ruleId, UpdateRuleRequestDto request) {
        ArchitectureRule rule = findRuleOrThrow(ruleId);

        if (request.getIsEnabled() != null) {
            rule.setIsEnabled(request.getIsEnabled());
        }

        RuleVersion currentVersion = ruleVersionRepository
            .findByRuleIdAndVersionNumber(rule.getId(), rule.getCurrentVersion())
            .orElse(null);

        String currentBoundary = currentVersion != null ? currentVersion.getBoundary() : "";
        String newName = (request.getName() != null && !request.getName().isBlank())
            ? request.getName().trim()
            : rule.getName();
        String newBoundary = (request.getBoundary() != null && !request.getBoundary().isBlank())
            ? request.getBoundary().trim()
            : currentBoundary;

        boolean contentChanged = !newName.equals(rule.getName()) || !newBoundary.equals(currentBoundary);

        if (contentChanged) {
            int nextVersionNumber = rule.getCurrentVersion() + 1;
            rule.setCurrentVersion(nextVersionNumber);
            rule.setName(newName);

            RuleVersion nextVersion = RuleVersion.builder()
                .rule(rule)
                .versionNumber(nextVersionNumber)
                .name(newName)
                .boundary(newBoundary)
                .build();

            ruleVersionRepository.save(nextVersion);
            log.info("Created new immutable version {} for ArchitectureRule '{}' (id: {})",
                nextVersionNumber, rule.getName(), rule.getId());
        }

        ArchitectureRule updatedRule = ruleRepository.save(rule);
        return toResponseDto(updatedRule, newBoundary);
    }

    /**
     * Toggles a rule's active status without modifying boundary versions.
     */
    @Transactional
    public RuleResponseDto toggleRule(UUID ruleId, boolean isEnabled) {
        ArchitectureRule rule = findRuleOrThrow(ruleId);
        rule.setIsEnabled(isEnabled);
        ArchitectureRule saved = ruleRepository.save(rule);
        log.info("Toggled ArchitectureRule '{}' (id: {}) isEnabled to {}", saved.getName(), saved.getId(), isEnabled);
        return toResponseDto(saved);
    }

    /**
     * Deletes a rule and its associated historical version snapshots.
     */
    @Transactional
    public void deleteRule(UUID ruleId) {
        ArchitectureRule rule = findRuleOrThrow(ruleId);
        List<RuleVersion> versions = ruleVersionRepository.findByRuleIdOrderByVersionNumberDesc(ruleId);
        ruleVersionRepository.deleteAll(versions);
        ruleRepository.delete(rule);
        log.info("Deleted ArchitectureRule '{}' (id: {}) and {} historical version snapshots",
            rule.getName(), ruleId, versions.size());
    }

    /**
     * Returns the full version history snapshots for a given rule.
     */
    @Transactional(readOnly = true)
    public List<RuleVersionDto> getRuleVersionHistory(UUID ruleId) {
        findRuleOrThrow(ruleId);
        List<RuleVersion> versions = ruleVersionRepository.findByRuleIdOrderByVersionNumberDesc(ruleId);
        return versions.stream()
            .map(v -> RuleVersionDto.builder()
                .id(v.getId())
                .versionNumber(v.getVersionNumber())
                .name(v.getName())
                .boundary(v.getBoundary())
                .createdAt(v.getCreatedAt())
                .build())
            .toList();
    }

    private ArchitectureRule findRuleOrThrow(UUID ruleId) {
        return ruleRepository.findById(ruleId)
            .orElseThrow(() -> new ResourceNotFoundException("Architecture rule not found with id: " + ruleId));
    }

    private RuleResponseDto toResponseDto(ArchitectureRule rule) {
        Optional<RuleVersion> currentVersion = ruleVersionRepository
            .findByRuleIdAndVersionNumber(rule.getId(), rule.getCurrentVersion());
        String boundary = currentVersion.map(RuleVersion::getBoundary).orElse("");
        return toResponseDto(rule, boundary);
    }

    private RuleResponseDto toResponseDto(ArchitectureRule rule, String boundary) {
        return RuleResponseDto.builder()
            .id(rule.getId())
            .repositoryId(rule.getRepository().getId())
            .name(rule.getName())
            .boundary(boundary)
            .isEnabled(rule.getIsEnabled())
            .currentVersion(rule.getCurrentVersion())
            .createdAt(rule.getCreatedAt())
            .updatedAt(rule.getUpdatedAt())
            .build();
    }
}
