package me.dennislab.diffvera.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.installation.Installation;
import me.dennislab.diffvera.installation.InstallationRepository;
import me.dennislab.diffvera.pullrequest.PullRequest;
import me.dennislab.diffvera.pullrequest.PullRequestRepository;
import me.dennislab.diffvera.repository.GitRepoRepository;
import me.dennislab.diffvera.repository.Repository;
import me.dennislab.diffvera.review.ReviewRun;
import me.dennislab.diffvera.review.ReviewRunRepository;
import me.dennislab.diffvera.webhook.dto.WebhookResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service that dispatches and processes parsed GitHub webhook events,
 * updating installations, repositories, pull requests, and queuing review runs.
 */
@Service
@Slf4j
public class WebhookDispatcherService {

    private final InstallationRepository installationRepository;
    private final GitRepoRepository gitRepoRepository;
    private final PullRequestRepository pullRequestRepository;
    private final ReviewRunRepository reviewRunRepository;
    private final ObjectMapper objectMapper;
    private final me.dennislab.diffvera.review.ReviewWorkerService reviewWorkerService;

    public WebhookDispatcherService(
        InstallationRepository installationRepository,
        GitRepoRepository gitRepoRepository,
        PullRequestRepository pullRequestRepository,
        ReviewRunRepository reviewRunRepository
    ) {
        this(installationRepository, gitRepoRepository, pullRequestRepository, reviewRunRepository, new ObjectMapper(), null);
    }

    public WebhookDispatcherService(
        InstallationRepository installationRepository,
        GitRepoRepository gitRepoRepository,
        PullRequestRepository pullRequestRepository,
        ReviewRunRepository reviewRunRepository,
        ObjectMapper objectMapper
    ) {
        this(installationRepository, gitRepoRepository, pullRequestRepository, reviewRunRepository, objectMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WebhookDispatcherService(
        InstallationRepository installationRepository,
        GitRepoRepository gitRepoRepository,
        PullRequestRepository pullRequestRepository,
        ReviewRunRepository reviewRunRepository,
        ObjectMapper objectMapper,
        @org.springframework.beans.factory.annotation.Autowired(required = false) me.dennislab.diffvera.review.ReviewWorkerService reviewWorkerService
    ) {
        this.installationRepository = installationRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.reviewRunRepository = reviewRunRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.reviewWorkerService = reviewWorkerService;
    }

    /**
     * Dispatches an incoming event by its GitHub event type name.
     */
    @Transactional
    public WebhookResponseDto dispatch(String event, String deliveryId, String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);

            return switch (event) {
                case "ping" -> handlePing(deliveryId);
                case "pull_request" -> handlePullRequest(root, deliveryId);
                case "installation" -> handleInstallation(root, deliveryId);
                default -> {
                    log.info("Ignoring unsupported GitHub webhook event: {}", event);
                    yield WebhookResponseDto.builder()
                        .status("ignored")
                        .event(event)
                        .delivery(deliveryId)
                        .message("Ignored unsupported event type.")
                        .build();
                }
            };
        } catch (Exception e) {
            log.error("Failed to parse and dispatch webhook payload: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Malformed webhook JSON payload: " + e.getMessage());
        }
    }

    private WebhookResponseDto handlePing(String deliveryId) {
        log.info("Received GitHub ping event (delivery: {})", deliveryId);
        return WebhookResponseDto.builder()
            .status("processed")
            .event("ping")
            .delivery(deliveryId)
            .message("PONG")
            .build();
    }

    private WebhookResponseDto handlePullRequest(JsonNode root, String deliveryId) {
        String action = root.path("action").asText();
        JsonNode prNode = root.path("pull_request");
        JsonNode repoNode = root.path("repository");
        JsonNode installNode = root.path("installation");

        int prNumber = prNode.path("number").asInt();
        String headSha = prNode.path("head").path("sha").asText();
        String baseSha = prNode.path("base").path("sha").asText();

        log.info("Processing pull_request event [action: {}, PR #{}, head: {}]", action, prNumber, headSha);

        // 1. Resolve or create installation record
        Installation installation = resolveInstallation(installNode);

        // 2. Resolve or create repository record
        Repository repository = resolveRepository(repoNode, installation);

        // 3. Process action
        if ("closed".equalsIgnoreCase(action)) {
            pullRequestRepository.findByRepositoryIdAndPrNumber(repository.getId(), prNumber)
                .ifPresent(pr -> {
                    pr.setStatus("closed");
                    pullRequestRepository.save(pr);
                });
            return WebhookResponseDto.builder()
                .status("processed")
                .event("pull_request")
                .delivery(deliveryId)
                .message(String.format("Marked PR #%d as closed.", prNumber))
                .build();
        }

        if ("opened".equalsIgnoreCase(action) || "synchronize".equalsIgnoreCase(action) || "reopened".equalsIgnoreCase(action)) {
            PullRequest pr = pullRequestRepository.findByRepositoryIdAndPrNumber(repository.getId(), prNumber)
                .orElseGet(() -> PullRequest.builder()
                    .repository(repository)
                    .prNumber(prNumber)
                    .build());

            pr.setGithubPrId(prNode.path("id").asLong());
            pr.setTitle(prNode.path("title").asText("Untitled PR"));
            pr.setAuthorLogin(prNode.path("user").path("login").asText("unknown"));
            pr.setBaseBranch(prNode.path("base").path("ref").asText("main"));
            pr.setHeadBranch(prNode.path("head").path("ref").asText("feature"));
            pr.setBaseSha(baseSha);
            pr.setHeadSha(headSha);
            pr.setStatus("open");
            pr.setAdditions(prNode.path("additions").asInt(0));
            pr.setDeletions(prNode.path("deletions").asInt(0));

            pr = pullRequestRepository.save(pr);

            // 4. Queue review run if none already exists for this commit SHA
            Optional<ReviewRun> existingRun = reviewRunRepository.findByPullRequestIdAndCommitShaAndStatusIn(
                pr.getId(), headSha, List.of("queued", "running", "completed")
            );

            if (existingRun.isEmpty()) {
                ReviewRun reviewRun = ReviewRun.builder()
                    .pullRequest(pr)
                    .commitSha(headSha)
                    .status("queued")
                    .requestedAt(OffsetDateTime.now())
                    .build();
                ReviewRun savedRun = reviewRunRepository.save(reviewRun);
                log.info("Queued review run for PR #{} commit {}", prNumber, headSha);

                if (reviewWorkerService != null) {
                    reviewWorkerService.processReviewAsync(savedRun.getId());
                }
            }

            return WebhookResponseDto.builder()
                .status("processed")
                .event("pull_request")
                .delivery(deliveryId)
                .message(String.format("Synchronized PR #%d and queued review for commit %s.", prNumber, headSha))
                .build();
        }

        return WebhookResponseDto.builder()
            .status("ignored")
            .event("pull_request")
            .delivery(deliveryId)
            .message("Ignored pull_request action: " + action)
            .build();
    }

    private WebhookResponseDto handleInstallation(JsonNode root, String deliveryId) {
        String action = root.path("action").asText();
        JsonNode installNode = root.path("installation");
        long installId = installNode.path("id").asLong();
        String accountLogin = installNode.path("account").path("login").asText();
        String accountType = installNode.path("account").path("type").asText("Organization");

        log.info("Processing installation event [action: {}, ID: {}, account: {}]", action, installId, accountLogin);

        if ("created".equalsIgnoreCase(action) || "unsuspend".equalsIgnoreCase(action)) {
            Installation inst = installationRepository.findByGithubInstallationId(installId)
                .orElseGet(() -> Installation.builder()
                    .githubInstallationId(installId)
                    .build());
            inst.setAccountLogin(accountLogin);
            inst.setAccountType(accountType);
            inst.setStatus("connected");
            installationRepository.save(inst);
            return WebhookResponseDto.builder()
                .status("processed")
                .event("installation")
                .delivery(deliveryId)
                .message(String.format("Connected installation for %s.", accountLogin))
                .build();
        }

        if ("deleted".equalsIgnoreCase(action) || "suspend".equalsIgnoreCase(action)) {
            installationRepository.findByGithubInstallationId(installId).ifPresent(inst -> {
                inst.setStatus("revoked");
                installationRepository.save(inst);
            });
            return WebhookResponseDto.builder()
                .status("processed")
                .event("installation")
                .delivery(deliveryId)
                .message(String.format("Revoked installation for %s.", accountLogin))
                .build();
        }

        return WebhookResponseDto.builder()
            .status("ignored")
            .event("installation")
            .delivery(deliveryId)
            .message("Ignored installation action: " + action)
            .build();
    }

    private Installation resolveInstallation(JsonNode installNode) {
        long installId = installNode.path("id").asLong(0L);
        if (installId <= 0L) {
            return installationRepository.findAll().stream().findFirst().orElseGet(() ->
                installationRepository.save(Installation.builder()
                    .githubInstallationId(999999L)
                    .accountLogin("diffvera-account")
                    .accountType("User")
                    .status("connected")
                    .build())
            );
        }

        return installationRepository.findByGithubInstallationId(installId)
            .orElseGet(() -> installationRepository.save(Installation.builder()
                .githubInstallationId(installId)
                .accountLogin(installNode.path("account").path("login").asText("account"))
                .accountType(installNode.path("account").path("type").asText("Organization"))
                .status("connected")
                .build()));
    }

    private Repository resolveRepository(JsonNode repoNode, Installation installation) {
        long repoId = repoNode.path("id").asLong();
        return gitRepoRepository.findByGithubRepoId(repoId)
            .orElseGet(() -> gitRepoRepository.save(Repository.builder()
                .installation(installation)
                .githubRepoId(repoId)
                .name(repoNode.path("name").asText("repo"))
                .fullName(repoNode.path("full_name").asText("owner/repo"))
                .isPrivate(repoNode.path("private").asBoolean(false))
                .defaultBranch(repoNode.path("default_branch").asText("main"))
                .accessStatus("available")
                .build()));
    }
}
