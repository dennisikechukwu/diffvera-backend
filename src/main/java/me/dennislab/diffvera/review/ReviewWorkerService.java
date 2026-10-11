package me.dennislab.diffvera.review;

import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.diff.GitHubDiffService;
import me.dennislab.diffvera.diff.dto.GitHubFileDiffDto;
import me.dennislab.diffvera.installation.Installation;
import me.dennislab.diffvera.pullrequest.PullRequest;
import me.dennislab.diffvera.pullrequest.PullRequestRepository;
import me.dennislab.diffvera.repository.Repository;
import me.dennislab.diffvera.review.gemini.GeminiAiService;
import me.dennislab.diffvera.review.gemini.GeminiFindingItem;
import me.dennislab.diffvera.rule.ArchitectureRule;
import me.dennislab.diffvera.rule.ArchitectureRuleRepository;
import me.dennislab.diffvera.rule.RuleVersion;
import me.dennislab.diffvera.rule.RuleVersionRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Worker service responsible for orchestrating the end-to-end AI code review pipeline:
 * 1. Marking review run in-progress.
 * 2. Snapshotting current active architecture rules.
 * 3. Fetching and filtering PR diffs via GitHub Compare API.
 * 4. Requesting structured evaluation from Google Gemini.
 * 5. Persisting findings and completing the review run.
 */
@Service
@Slf4j
public class ReviewWorkerService {

    private final ReviewRunRepository reviewRunRepository;
    private final PullRequestRepository pullRequestRepository;
    private final FindingRepository findingRepository;
    private final ArchitectureRuleRepository ruleRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final GitHubDiffService diffService;
    private final GeminiAiService geminiAiService;

    public ReviewWorkerService(
        ReviewRunRepository reviewRunRepository,
        PullRequestRepository pullRequestRepository,
        FindingRepository findingRepository,
        ArchitectureRuleRepository ruleRepository,
        RuleVersionRepository ruleVersionRepository,
        GitHubDiffService diffService,
        GeminiAiService geminiAiService
    ) {
        this.reviewRunRepository = reviewRunRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.findingRepository = findingRepository;
        this.ruleRepository = ruleRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.diffService = diffService;
        this.geminiAiService = geminiAiService;
    }

    /**
     * Executes the review asynchronously on a background worker thread.
     */
    @Async
    public CompletableFuture<ReviewRun> processReviewAsync(UUID reviewRunId) {
        log.info("Starting asynchronous review task for ReviewRun ID: {}", reviewRunId);
        ReviewRun completed = processReview(reviewRunId);
        return CompletableFuture.completedFuture(completed);
    }

    /**
     * Synchronously runs the AI code review pipeline.
     */
    @Transactional
    public ReviewRun processReview(UUID reviewRunId) {
        log.info("Processing AI code review for run {}", reviewRunId);

        Optional<ReviewRun> runOpt = Optional.empty();
        for (int i = 0; i < 5; i++) {
            runOpt = reviewRunRepository.findByIdWithDetails(reviewRunId)
                .or(() -> reviewRunRepository.findById(reviewRunId));
            if (runOpt.isPresent()) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (runOpt.isEmpty()) {
            log.error("ReviewRun {} not found in database; aborting review.", reviewRunId);
            return null;
        }

        ReviewRun reviewRun = runOpt.get();
        if ("completed".equalsIgnoreCase(reviewRun.getStatus())) {
            log.warn("ReviewRun {} is already in terminal state 'completed'; skipping execution.", reviewRunId);
            return reviewRun;
        }

        try {
            // 1. Mark in progress
            reviewRun.setStatus("running");
            reviewRun.setStartedAt(OffsetDateTime.now());
            reviewRunRepository.save(reviewRun);

            PullRequest pr = reviewRun.getPullRequest();
            Repository repo = pr.getRepository();
            Installation installation = repo.getInstallation();

            // 2. Snapshot current active architecture rules
            List<RuleVersion> activeRuleVersions = snapshotActiveRules(repo.getId(), reviewRun);

            // 3. Fetch and sanitize changed file diffs
            Long installId = installation != null ? installation.getGithubInstallationId() : 0L;
            List<GitHubFileDiffDto> diffFiles = diffService.fetchCompareDiff(
                repo.getFullName(),
                pr.getBaseSha(),
                pr.getHeadSha(),
                installId
            );

            // 4. If no reviewable changes exist, complete with 0 findings
            if (diffFiles.isEmpty()) {
                log.info("No reviewable file changes found for PR #{} ({}). Completing review run with 0 findings.", pr.getPrNumber(), repo.getFullName());
                reviewRun.setStatus("completed");
                reviewRun.setCompletedAt(OffsetDateTime.now());
                return reviewRunRepository.save(reviewRun);
            }

            // 5. Query Google Gemini AI model
            List<GeminiFindingItem> geminiFindings = geminiAiService.analyzeDiff(
                repo.getFullName(),
                pr.getTitle(),
                activeRuleVersions,
                diffFiles
            );

            // 6. Map and persist findings
            persistFindings(reviewRun, activeRuleVersions, geminiFindings);

            // 7. Mark successfully completed
            reviewRun.setStatus("completed");
            reviewRun.setCompletedAt(OffsetDateTime.now());
            reviewRun = reviewRunRepository.save(reviewRun);

            log.info("Successfully completed AI review run {} for PR #{}. Generated {} findings.",
                reviewRunId, pr.getPrNumber(), geminiFindings.size());
            return reviewRun;

        } catch (Exception e) {
            log.error("Failed to complete review run {}: {}", reviewRunId, e.getMessage(), e);
            reviewRun.setStatus("failed");
            String reason = e.getMessage() != null ? e.getMessage() : "Unknown error";
            if (reason.contains("503") || reason.contains("high demand") || reason.contains("UNAVAILABLE")) {
                reason = "Google AI model is experiencing temporary high demand (503 Service Unavailable). Please click Retry Review.";
            }
            reviewRun.setFailureReason(reason);
            reviewRun.setCompletedAt(OffsetDateTime.now());
            return reviewRunRepository.save(reviewRun);
        }
    }

    private List<RuleVersion> snapshotActiveRules(UUID repoId, ReviewRun reviewRun) {
        List<ArchitectureRule> activeRules = ruleRepository.findByRepositoryIdAndIsEnabledTrue(repoId);
        List<RuleVersion> versions = new ArrayList<>();

        for (ArchitectureRule rule : activeRules) {
            ruleVersionRepository.findByRuleIdAndVersionNumber(rule.getId(), rule.getCurrentVersion())
                .ifPresent(versions::add);
        }

        reviewRun.setRuleVersions(new HashSet<>(versions));
        log.info("Attached {} active rule version snapshots to ReviewRun {}", versions.size(), reviewRun.getId());
        return versions;
    }

    private void persistFindings(
        ReviewRun reviewRun,
        List<RuleVersion> activeRuleVersions,
        List<GeminiFindingItem> geminiFindings
    ) {
        Map<String, RuleVersion> ruleMap = new HashMap<>();
        for (RuleVersion rv : activeRuleVersions) {
            ruleMap.put(rv.getName().toLowerCase(Locale.ROOT).trim(), rv);
        }

        for (GeminiFindingItem item : geminiFindings) {
            RuleVersion matchedRule = null;
            if (item.getRuleName() != null && !item.getRuleName().isBlank()) {
                matchedRule = ruleMap.get(item.getRuleName().toLowerCase(Locale.ROOT).trim());
            }

            Finding finding = Finding.builder()
                .reviewRun(reviewRun)
                .ruleVersion(matchedRule)
                .category(item.getCategory())
                .severity(item.getSeverity())
                .title(item.getTitle())
                .summary(item.getSummary())
                .filePath(item.getFilePath())
                .lineStart(item.getLineStart())
                .lineEnd(item.getLineEnd())
                .codeExcerpt(item.getCodeExcerpt())
                .explanation(item.getExplanation())
                .suggestedCorrection(item.getSuggestedCorrection())
                .build();

            findingRepository.save(finding);
        }
    }
}
