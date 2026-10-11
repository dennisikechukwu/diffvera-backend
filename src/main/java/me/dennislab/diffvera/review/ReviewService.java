package me.dennislab.diffvera.review;

import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.common.error.BadRequestException;
import me.dennislab.diffvera.common.error.ResourceNotFoundException;
import me.dennislab.diffvera.diff.GitHubDiffService;
import me.dennislab.diffvera.diff.dto.GitHubFileDiffDto;
import me.dennislab.diffvera.installation.Installation;
import me.dennislab.diffvera.pullrequest.PullRequest;
import me.dennislab.diffvera.pullrequest.PullRequestRepository;
import me.dennislab.diffvera.repository.Repository;
import me.dennislab.diffvera.review.dto.*;
import me.dennislab.diffvera.review.util.PatchParserUtil;
import me.dennislab.diffvera.rule.RuleVersion;
import me.dennislab.diffvera.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing review runs, findings evaluation, and developer feedback.
 */
@Service
@Slf4j
public class ReviewService {

    private final ReviewRunRepository reviewRunRepository;
    private final PullRequestRepository pullRequestRepository;
    private final FindingRepository findingRepository;
    private final FindingFeedbackRepository findingFeedbackRepository;
    private final GitHubDiffService diffService;
    private final ReviewWorkerService reviewWorkerService;

    public ReviewService(
        ReviewRunRepository reviewRunRepository,
        PullRequestRepository pullRequestRepository,
        FindingRepository findingRepository,
        FindingFeedbackRepository findingFeedbackRepository,
        GitHubDiffService diffService,
        ReviewWorkerService reviewWorkerService
    ) {
        this.reviewRunRepository = reviewRunRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.findingRepository = findingRepository;
        this.findingFeedbackRepository = findingFeedbackRepository;
        this.diffService = diffService;
        this.reviewWorkerService = reviewWorkerService;
    }

    /**
     * Retrieves full review details including pull request info, findings with feedback, and file diffs.
     */
    @Transactional(readOnly = true)
    public ReviewDetailDto getReviewById(UUID reviewRunId) {
        ReviewRun reviewRun = findReviewRunOrThrow(reviewRunId);
        PullRequest pr = reviewRun.getPullRequest();
        Repository repo = pr.getRepository();

        List<Finding> findings = findingRepository.findByReviewRunId(reviewRunId);
        List<FindingResponseDto> findingDtos = findings.stream()
            .map(this::toFindingResponseDto)
            .toList();

        List<ReviewFileDiffDto> fileDiffs = fetchFileDiffsSafely(repo, pr);

        return ReviewDetailDto.builder()
            .id(reviewRun.getId())
            .pullRequestId(pr.getId())
            .pullRequestNumber(pr.getPrNumber())
            .pullRequestTitle(pr.getTitle())
            .repositoryFullName(repo.getFullName())
            .commitSha(reviewRun.getCommitSha())
            .headSha(reviewRun.getCommitSha())
            .status(reviewRun.getStatus())
            .failureReason(reviewRun.getFailureReason())
            .createdAt(reviewRun.getRequestedAt())
            .requestedAt(reviewRun.getRequestedAt())
            .startedAt(reviewRun.getStartedAt())
            .completedAt(reviewRun.getCompletedAt())
            .findings(findingDtos)
            .files(fileDiffs)
            .build();
    }

    /**
     * Retrieves findings for a review run directly from the database without fetching file diffs.
     */
    @Transactional(readOnly = true)
    public List<FindingResponseDto> getFindingsForReview(UUID reviewRunId) {
        return findingRepository.findByReviewRunId(reviewRunId).stream()
            .map(this::toFindingResponseDto)
            .toList();
    }

    /**
     * Retrieves recent reviews optionally filtered by repository and status.
     */
    @Transactional(readOnly = true)
    public List<ReviewSummaryDto> getRecentReviews(UUID repositoryId, String status, int limit) {
        List<ReviewRun> runs;
        boolean hasRepo = repositoryId != null;
        boolean hasStatus = status != null && !status.isBlank();

        if (hasRepo && hasStatus) {
            runs = reviewRunRepository.findByPullRequestRepositoryIdAndStatusOrderByRequestedAtDesc(repositoryId, status.toLowerCase(Locale.ROOT));
        } else if (hasRepo) {
            runs = reviewRunRepository.findByPullRequestRepositoryIdOrderByRequestedAtDesc(repositoryId);
        } else if (hasStatus) {
            runs = reviewRunRepository.findByStatusOrderByRequestedAtDesc(status.toLowerCase(Locale.ROOT));
        } else {
            runs = reviewRunRepository.findAllByOrderByRequestedAtDesc();
        }

        int max = Math.max(1, Math.min(limit, 100));
        return runs.stream()
            .limit(max)
            .map(this::toReviewSummaryDto)
            .toList();
    }

    /**
     * Retrieves all review runs for a given pull request.
     */
    @Transactional(readOnly = true)
    public List<ReviewSummaryDto> getReviewsByPullRequest(UUID pullRequestId) {
        List<ReviewRun> runs = reviewRunRepository.findByPullRequestIdOrderByRequestedAtDesc(pullRequestId);
        return runs.stream().map(this::toReviewSummaryDto).toList();
    }

    /**
     * Manually triggers an AI code review for a pull request.
     * Idempotently deduplicates against active queued or running reviews on the same commit.
     */
    @Transactional
    public ReviewDetailDto triggerReview(UUID pullRequestId, User requestingUser) {
        PullRequest pr = pullRequestRepository.findById(pullRequestId)
            .orElseThrow(() -> new ResourceNotFoundException("Pull request not found with id: " + pullRequestId));

        // Deduplication check: return existing in-flight run if currently queued or running
        Optional<ReviewRun> existingActive = reviewRunRepository.findByPullRequestIdAndCommitShaAndStatusIn(
            pullRequestId, pr.getHeadSha(), List.of("queued", "running")
        );
        if (existingActive.isPresent()) {
            ReviewRun active = existingActive.get();
            log.info("Found existing in-flight review run {} for PR #{} at commit {}; re-dispatching worker if dormant.",
                active.getId(), pr.getPrNumber(), pr.getHeadSha());
            if ("queued".equalsIgnoreCase(active.getStatus())) {
                reviewWorkerService.processReviewAsync(active.getId());
            }
            return getReviewById(active.getId());
        }

        // Create new queued review run
        ReviewRun newRun = ReviewRun.builder()
            .pullRequest(pr)
            .commitSha(pr.getHeadSha())
            .status("queued")
            .requestedByUser(requestingUser)
            .requestedAt(OffsetDateTime.now())
            .build();

        ReviewRun savedRun = reviewRunRepository.save(newRun);
        log.info("Queued manual review run {} for PR #{} ({})", savedRun.getId(), pr.getPrNumber(), pr.getRepository().getFullName());

        // Dispatch background review worker strictly after transaction commits
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reviewWorkerService.processReviewAsync(savedRun.getId());
                }
            });
        } else {
            reviewWorkerService.processReviewAsync(savedRun.getId());
        }

        return getReviewById(savedRun.getId());
    }

    /**
     * Submits or updates developer feedback on an individual finding.
     */
    @Transactional
    public FindingResponseDto submitFindingFeedback(UUID findingId, FindingFeedbackRequestDto request, User user) {
        Finding finding = findingRepository.findById(findingId)
            .orElseThrow(() -> new ResourceNotFoundException("Finding not found with id: " + findingId));

        String rawFeedback = request.getFeedback().trim().toLowerCase(Locale.ROOT);
        String normalizedFeedback;
        if ("false-positive".equals(rawFeedback) || "false_positive".equals(rawFeedback)) {
            normalizedFeedback = "false_positive";
        } else if ("useful".equals(rawFeedback)) {
            normalizedFeedback = "useful";
        } else if ("resolved".equals(rawFeedback)) {
            normalizedFeedback = "resolved";
        } else {
            throw new BadRequestException("Invalid feedback type: " + request.getFeedback() + ". Expected: useful, false-positive, or resolved.");
        }

        FindingFeedback feedbackRecord = findingFeedbackRepository.findByFindingIdAndUserId(findingId, user.getId())
            .orElseGet(() -> FindingFeedback.builder()
                .finding(finding)
                .user(user)
                .build());

        feedbackRecord.setFeedback(normalizedFeedback);
        findingFeedbackRepository.save(feedbackRecord);

        log.info("Recorded feedback '{}' from user {} on finding {}", normalizedFeedback, user.getLogin(), findingId);

        return toFindingResponseDto(finding, normalizedFeedback);
    }

    /**
     * Retrieves an individual finding by UUID.
     */
    @Transactional(readOnly = true)
    public FindingResponseDto getFindingById(UUID findingId) {
        Finding finding = findingRepository.findById(findingId)
            .orElseThrow(() -> new ResourceNotFoundException("Finding not found with id: " + findingId));
        return toFindingResponseDto(finding);
    }

    private ReviewRun findReviewRunOrThrow(UUID reviewRunId) {
        return reviewRunRepository.findById(reviewRunId)
            .orElseThrow(() -> new ResourceNotFoundException("Review run not found with id: " + reviewRunId));
    }

    private List<ReviewFileDiffDto> fetchFileDiffsSafely(Repository repo, PullRequest pr) {
        try {
            Installation install = repo.getInstallation();
            Long installId = install != null ? install.getGithubInstallationId() : 0L;
            List<GitHubFileDiffDto> rawDiffs = diffService.fetchCompareDiff(
                repo.getFullName(),
                pr.getBaseSha(),
                pr.getHeadSha(),
                installId
            );
            return PatchParserUtil.parseDiffs(rawDiffs);
        } catch (Exception e) {
            log.warn("Could not retrieve file diffs for {} (PR #{}) from GitHub: {}",
                repo.getFullName(), pr.getPrNumber(), e.getMessage());
            return List.of();
        }
    }

    private FindingResponseDto toFindingResponseDto(Finding f) {
        String feedback = findingFeedbackRepository.findByFindingId(f.getId())
            .map(FindingFeedback::getFeedback)
            .orElse(null);
        return toFindingResponseDto(f, feedback);
    }

    private FindingResponseDto toFindingResponseDto(Finding f, String feedback) {
        RuleVersion rv = f.getRuleVersion();
        UUID ruleVersionId = rv != null ? rv.getId() : null;
        UUID ruleId = (rv != null && rv.getRule() != null) ? rv.getRule().getId() : null;
        String ruleName = rv != null ? rv.getName() : null;

        // Frontend format uses "false-positive"
        String formattedFeedback = "false_positive".equals(feedback) ? "false-positive" : feedback;

        return FindingResponseDto.builder()
            .id(f.getId())
            .reviewRunId(f.getReviewRun().getId())
            .ruleVersionId(ruleVersionId)
            .ruleId(ruleId)
            .ruleName(ruleName)
            .category(f.getCategory())
            .severity(f.getSeverity())
            .title(f.getTitle())
            .summary(f.getSummary())
            .filePath(f.getFilePath())
            .file(f.getFilePath())
            .lineStart(f.getLineStart())
            .startLine(f.getLineStart())
            .lineEnd(f.getLineEnd())
            .endLine(f.getLineEnd())
            .codeExcerpt(f.getCodeExcerpt())
            .explanation(f.getExplanation())
            .suggestion(f.getSummary())
            .correction(f.getSuggestedCorrection())
            .suggestedCorrection(f.getSuggestedCorrection())
            .feedback(formattedFeedback)
            .createdAt(f.getCreatedAt())
            .build();
    }

    private ReviewSummaryDto toReviewSummaryDto(ReviewRun run) {
        PullRequest pr = run.getPullRequest();
        Repository repo = pr.getRepository();
        long findingCount = findingRepository.countByReviewRunId(run.getId());
        long highSeverityCount = findingRepository.countByReviewRunIdAndSeverity(run.getId(), "high");

        return ReviewSummaryDto.builder()
            .id(run.getId())
            .pullRequestId(pr.getId())
            .pullRequestNumber(pr.getPrNumber())
            .pullRequestTitle(pr.getTitle())
            .repositoryFullName(repo.getFullName())
            .commitSha(run.getCommitSha())
            .headSha(run.getCommitSha())
            .status(run.getStatus())
            .failureReason(run.getFailureReason())
            .requestedAt(run.getRequestedAt())
            .startedAt(run.getStartedAt())
            .completedAt(run.getCompletedAt())
            .findingCount(findingCount)
            .highSeverityCount(highSeverityCount)
            .build();
    }
}
