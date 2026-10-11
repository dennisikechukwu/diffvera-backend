package me.dennislab.diffvera.pullrequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.common.error.ResourceNotFoundException;
import me.dennislab.diffvera.installation.github.GitHubAppAuthService;
import me.dennislab.diffvera.pullrequest.dto.PullRequestDto;
import me.dennislab.diffvera.repository.GitRepoRepository;
import me.dennislab.diffvera.repository.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * Service managing pull request queries and details.
 */
@Service
@Slf4j
public class PullRequestService {

    private final PullRequestRepository pullRequestRepository;
    private final GitRepoRepository gitRepoRepository;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PullRequestService(PullRequestRepository pullRequestRepository) {
        this(pullRequestRepository, null, null, null, null);
    }

    @Autowired
    public PullRequestService(
        PullRequestRepository pullRequestRepository,
        GitRepoRepository gitRepoRepository,
        GitHubAppAuthService gitHubAppAuthService,
        RestClient restClient,
        ObjectMapper objectMapper
    ) {
        this.pullRequestRepository = pullRequestRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.restClient = restClient != null ? restClient : RestClient.builder().build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    private final java.util.concurrent.ConcurrentHashMap<UUID, java.time.Instant> lastSyncedByRepo = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Synchronizes open pull requests directly from GitHub for a given repository.
     */
    @Transactional
    public void syncPullRequestsFromGitHubIfConfigured(UUID repositoryId) {
        if (repositoryId == null || gitHubAppAuthService == null || gitRepoRepository == null) {
            return;
        }
        var lastSynced = lastSyncedByRepo.get(repositoryId);
        if (lastSynced != null && java.time.Duration.between(lastSynced, java.time.Instant.now()).toSeconds() < 60) {
            log.debug("Skipping GitHub PR sync for repo {}, synced recently", repositoryId);
            return;
        }
        var repoOpt = gitRepoRepository.findById(repositoryId);
        if (repoOpt.isEmpty()) {
            return;
        }
        Repository repo = repoOpt.get();
        if (repo.getInstallation() == null || repo.getInstallation().getGithubInstallationId() == null) {
            return;
        }

        var tokenOpt = gitHubAppAuthService.getInstallationToken(repo.getInstallation().getGithubInstallationId());
        if (tokenOpt.isEmpty()) {
            return;
        }
        try {
            String url = "https://api.github.com/repos/" + repo.getFullName() + "/pulls?state=all&per_page=30";
            String json = restClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOpt.get())
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("User-Agent", "Diffvera-Backend")
                .retrieve()
                .body(String.class);

            if (json == null || json.isBlank()) {
                lastSyncedByRepo.put(repositoryId, java.time.Instant.now());
                return;
            }

            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                lastSyncedByRepo.put(repositoryId, java.time.Instant.now());
                return;
            }

            for (JsonNode prNode : root) {
                int prNumber = prNode.path("number").asInt();
                long githubPrId = prNode.path("id").asLong();
                String title = prNode.path("title").asText("Untitled PR");
                String authorLogin = prNode.path("user").path("login").asText("unknown");
                String baseBranch = prNode.path("base").path("ref").asText("main");
                String headBranch = prNode.path("head").path("ref").asText("feature");
                String baseSha = prNode.path("base").path("sha").asText();
                String headSha = prNode.path("head").path("sha").asText();
                String state = prNode.path("state").asText("open");

                PullRequest pr = pullRequestRepository.findByRepositoryIdAndPrNumber(repo.getId(), prNumber)
                    .orElseGet(() -> PullRequest.builder()
                        .repository(repo)
                        .prNumber(prNumber)
                        .build());

                pr.setGithubPrId(githubPrId);
                pr.setTitle(title);
                pr.setAuthorLogin(authorLogin);
                pr.setBaseBranch(baseBranch);
                pr.setHeadBranch(headBranch);
                pr.setBaseSha(baseSha);
                pr.setHeadSha(headSha);
                pr.setStatus(state.toLowerCase());
                int additions = 0;
                int deletions = 0;
                if (prNode.has("additions")) {
                    additions = prNode.path("additions").asInt(0);
                }
                if (prNode.has("deletions")) {
                    deletions = prNode.path("deletions").asInt(0);
                }

                if (additions == 0 && deletions == 0 && pr.getId() != null && pr.getAdditions() != null && pr.getDeletions() != null) {
                    additions = pr.getAdditions();
                    deletions = pr.getDeletions();
                }

                // GitHub's pulls list endpoint omits additions & deletions; fetch single PR detail only for open PRs needing diff counts
                if (additions == 0 && deletions == 0 && "open".equalsIgnoreCase(state)) {
                    try {
                        String singlePrUrl = "https://api.github.com/repos/" + repo.getFullName() + "/pulls/" + prNumber;
                        String singleJson = restClient.get()
                            .uri(singlePrUrl)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOpt.get())
                            .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                            .header("User-Agent", "Diffvera-Backend")
                            .retrieve()
                            .body(String.class);
                        if (singleJson != null && !singleJson.isBlank()) {
                            JsonNode singleNode = objectMapper.readTree(singleJson);
                            additions = singleNode.path("additions").asInt(0);
                            deletions = singleNode.path("deletions").asInt(0);
                        }
                    } catch (Exception ex) {
                        log.debug("Could not fetch additions/deletions for PR #{} on {}: {}", prNumber, repo.getFullName(), ex.getMessage());
                    }
                }

                pr.setAdditions(additions);
                pr.setDeletions(deletions);
                pullRequestRepository.save(pr);
            }
            lastSyncedByRepo.put(repositoryId, java.time.Instant.now());
        } catch (Exception e) {
            log.warn("Failed to fetch pull requests from GitHub for repo {}: {}", repo.getFullName(), e.getMessage());
        }
    }

    /**
     * Retrieves pull requests for a given repository, optionally filtered by status ("open", "merged", "closed").
     */
    @Transactional
    public List<PullRequestDto> getPullRequestsByRepository(UUID repositoryId, String status) {
        syncPullRequestsFromGitHubIfConfigured(repositoryId);
        List<PullRequest> prs;
        if (status != null && !status.isBlank()) {
            prs = pullRequestRepository.findByRepositoryIdAndStatus(repositoryId, status.toLowerCase());
        } else {
            prs = pullRequestRepository.findByRepositoryId(repositoryId);
        }
        return prs.stream().map(this::toDto).toList();
    }

    /**
     * Retrieves a pull request by its unique ID.
     */
    @Transactional(readOnly = true)
    public PullRequestDto getPullRequestById(UUID pullRequestId) {
        PullRequest pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow(() -> new ResourceNotFoundException("Pull request not found with id: " + pullRequestId));
        return toDto(pr);
    }

    public PullRequestDto toDto(PullRequest pr) {
        return PullRequestDto.builder()
            .id(pr.getId())
            .repositoryId(pr.getRepository().getId())
            .githubPrId(pr.getGithubPrId())
            .number(pr.getPrNumber())
            .title(pr.getTitle())
            .author(pr.getAuthorLogin())
            .baseBranch(pr.getBaseBranch())
            .headBranch(pr.getHeadBranch())
            .branch(pr.getHeadBranch())
            .baseSha(pr.getBaseSha())
            .headSha(pr.getHeadSha())
            .status(pr.getStatus())
            .additions(pr.getAdditions())
            .deletions(pr.getDeletions())
            .createdAt(pr.getCreatedAt())
            .updatedAt(pr.getUpdatedAt())
            .build();
    }
}
