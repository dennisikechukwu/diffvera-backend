package me.dennislab.diffvera.repository;

import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.installation.Installation;
import me.dennislab.diffvera.installation.InstallationRepository;
import me.dennislab.diffvera.installation.github.GitHubAppAuthService;
import me.dennislab.diffvera.installation.github.dto.GitHubInstallationRepoResponse;
import me.dennislab.diffvera.installation.github.dto.GitHubRepoItemDto;
import me.dennislab.diffvera.pullrequest.PullRequestRepository;
import me.dennislab.diffvera.repository.dto.RepositoryDto;
import me.dennislab.diffvera.repository.dto.RepositorySyncResultDto;
import me.dennislab.diffvera.rule.ArchitectureRuleRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service managing repository ingestion, synchronization against GitHub,
 * and mapping repositories to enriched governance DTOs.
 */
@Service
@Slf4j
public class RepositorySyncService {

    private final InstallationRepository installationRepository;
    private final GitRepoRepository gitRepoRepository;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final ArchitectureRuleRepository architectureRuleRepository;
    private final PullRequestRepository pullRequestRepository;
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public RepositorySyncService(
        InstallationRepository installationRepository,
        GitRepoRepository gitRepoRepository,
        GitHubAppAuthService gitHubAppAuthService,
        ArchitectureRuleRepository architectureRuleRepository,
        PullRequestRepository pullRequestRepository
    ) {
        this.installationRepository = installationRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.architectureRuleRepository = architectureRuleRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.restClient = RestClient.builder().build();
    }

    public RepositorySyncService(
        InstallationRepository installationRepository,
        GitRepoRepository gitRepoRepository,
        GitHubAppAuthService gitHubAppAuthService,
        ArchitectureRuleRepository architectureRuleRepository,
        PullRequestRepository pullRequestRepository,
        RestClient restClient
    ) {
        this.installationRepository = installationRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.architectureRuleRepository = architectureRuleRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.restClient = restClient != null ? restClient : RestClient.builder().build();
    }

    /**
     * Lists all connected repositories with active architecture rule and PR counts.
     */
    @Transactional(readOnly = true)
    public List<RepositoryDto> getAllRepositories() {
        return gitRepoRepository.findAllNewestFirst().stream()
            .map(this::toDto)
            .toList();
    }

    /**
     * Lists connected repositories accessible to the given user.
     */
    @Transactional(readOnly = true)
    public List<RepositoryDto> getRepositoriesForUser(me.dennislab.diffvera.user.User user) {
        if (user == null || user.getLogin() == null) {
            return List.of();
        }
        List<RepositoryDto> repos = gitRepoRepository.findAccessibleRepositories(user.getLogin()).stream()
            .map(this::toDto)
            .toList();
        if (repos.isEmpty()) {
            discoverAndSyncInstallations();
            return gitRepoRepository.findAccessibleRepositories(user.getLogin()).stream()
                .map(this::toDto)
                .toList();
        }
        return repos;
    }

    /**
     * Discovers active installations from GitHub App and synchronizes their repositories.
     */
    @Transactional
    public void discoverAndSyncInstallations() {
        var remoteInstallations = gitHubAppAuthService.fetchAppInstallations();
        for (var remote : remoteInstallations) {
            if (remote.id() != null && remote.account() != null && remote.account().login() != null) {
                Installation inst = installationRepository.findByGithubInstallationId(remote.id())
                    .orElseGet(() -> Installation.builder()
                        .githubInstallationId(remote.id())
                        .accountLogin(remote.account().login())
                        .accountType(remote.account().type() != null ? remote.account().type() : "User")
                        .status("connected")
                        .build());
                inst.setAccountLogin(remote.account().login());
                inst.setAccountType(remote.account().type() != null ? remote.account().type() : "User");
                inst.setStatus("connected");
                Installation saved = installationRepository.save(inst);
                syncInstallation(saved);
            }
        }
    }

    /**
     * Synchronizes repositories across all active GitHub App installations.
     */
    @Transactional
    public RepositorySyncResultDto syncAllInstallations() {
        discoverAndSyncInstallations();
        List<Installation> installations = installationRepository.findAll();
        if (installations.isEmpty()) {
            log.info("No GitHub App installations found to sync.");
            return RepositorySyncResultDto.builder()
                .status("completed")
                .syncedRepositoriesCount(0)
                .message("No active installations found to synchronize.")
                .build();
        }

        int totalSynced = 0;
        for (Installation installation : installations) {
            totalSynced += syncInstallation(installation);
        }

        log.info("Completed repository sync: {} repositories synced across {} installations.", totalSynced, installations.size());
        return RepositorySyncResultDto.builder()
            .status("completed")
            .syncedRepositoriesCount(totalSynced)
            .message(String.format("Synchronized %d repositories across %d installations.", totalSynced, installations.size()))
            .build();
    }

    /**
     * Queries GitHub for a single installation and upserts accessible repositories.
     */
    @Transactional
    public int syncInstallation(Installation installation) {
        Optional<String> tokenOpt = gitHubAppAuthService.getInstallationToken(installation.getGithubInstallationId());
        if (tokenOpt.isEmpty()) {
            log.warn("Cannot sync installation {} (account: {}): No installation token obtained.",
                installation.getGithubInstallationId(), installation.getAccountLogin());
            return 0;
        }

        String token = tokenOpt.get();
        try {
            GitHubInstallationRepoResponse response = restClient.get()
                .uri("https://api.github.com/installation/repositories?per_page=100")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("User-Agent", "Diffvera-Backend")
                .retrieve()
                .body(GitHubInstallationRepoResponse.class);

            if (response == null || response.getRepositories() == null) {
                return 0;
            }

            int count = 0;
            for (GitHubRepoItemDto item : response.getRepositories()) {
                upsertRepository(installation, item);
                count++;
            }
            return count;
        } catch (Exception e) {
            log.error("Failed to query repositories for installation {}: {}", installation.getGithubInstallationId(), e.getMessage());
            return 0;
        }
    }

    /**
     * Upserts an individual repository record from a GitHub item DTO.
     */
    @Transactional
    public Repository upsertRepository(Installation installation, GitHubRepoItemDto item) {
        Repository repo = gitRepoRepository.findByGithubRepoId(item.getId())
            .orElseGet(() -> Repository.builder()
                .githubRepoId(item.getId())
                .installation(installation)
                .build());

        repo.setInstallation(installation);
        repo.setName(item.getName());
        repo.setFullName(item.getFullName());
        repo.setIsPrivate(Boolean.TRUE.equals(item.getIsPrivate()));
        repo.setLanguage(item.getLanguage());
        repo.setDefaultBranch(item.getDefaultBranch() != null ? item.getDefaultBranch() : "main");
        repo.setAccessStatus("available");

        OffsetDateTime gitHubPushedAt = parseIsoDateTime(item.getPushedAt());
        OffsetDateTime gitHubUpdatedAt = parseIsoDateTime(item.getUpdatedAt());
        OffsetDateTime gitHubCreatedAt = parseIsoDateTime(item.getCreatedAt());

        if (gitHubPushedAt != null) {
            repo.setPushedAt(gitHubPushedAt);
        }
        if (gitHubUpdatedAt != null) {
            repo.setUpdatedAt(gitHubUpdatedAt);
        }
        if (gitHubCreatedAt != null) {
            repo.setCreatedAt(gitHubCreatedAt);
        }

        return gitRepoRepository.save(repo);
    }

    /**
     * Parses ISO-8601 string from GitHub API into OffsetDateTime.
     */
    private OffsetDateTime parseIsoDateTime(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(iso);
        } catch (Exception e) {
            try {
                return java.time.Instant.parse(iso).atOffset(java.time.ZoneOffset.UTC);
            } catch (Exception ex) {
                log.warn("Could not parse date time string '{}': {}", iso, ex.getMessage());
                return null;
            }
        }
    }

    /**
     * Enriches a Repository entity into a RepositoryDto with live metrics.
     */
    public RepositoryDto toDto(Repository repository) {
        long activeRules = architectureRuleRepository.countByRepositoryIdAndIsEnabledTrue(repository.getId());
        long openPrs = pullRequestRepository.countByRepositoryIdAndStatus(repository.getId(), "open");

        return RepositoryDto.builder()
            .id(repository.getId())
            .githubRepoId(repository.getGithubRepoId())
            .name(repository.getName())
            .fullName(repository.getFullName())
            .isPrivate(repository.getIsPrivate())
            .language(repository.getLanguage())
            .defaultBranch(repository.getDefaultBranch())
            .accessStatus(repository.getAccessStatus())
            .activeRuleCount(activeRules)
            .openPrCount(openPrs)
            .updatedAt(repository.getUpdatedAt())
            .pushedAt(repository.getPushedAt())
            .build();
    }
}
