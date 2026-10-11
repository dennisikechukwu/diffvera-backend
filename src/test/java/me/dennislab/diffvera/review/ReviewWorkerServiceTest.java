package me.dennislab.diffvera.review;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewWorkerServiceTest {

    @Mock
    private ReviewRunRepository reviewRunRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private FindingRepository findingRepository;

    @Mock
    private ArchitectureRuleRepository ruleRepository;

    @Mock
    private RuleVersionRepository ruleVersionRepository;

    @Mock
    private GitHubDiffService diffService;

    @Mock
    private GeminiAiService geminiAiService;

    private ReviewWorkerService workerService;

    private UUID runId;
    private Repository repository;
    private PullRequest pullRequest;
    private ReviewRun reviewRun;

    @BeforeEach
    void setUp() {
        workerService = new ReviewWorkerService(
            reviewRunRepository,
            pullRequestRepository,
            findingRepository,
            ruleRepository,
            ruleVersionRepository,
            diffService,
            geminiAiService
        );

        runId = UUID.randomUUID();

        Installation installation = Installation.builder()
            .id(UUID.randomUUID())
            .githubInstallationId(998877L)
            .accountLogin("octocat")
            .build();

        repository = Repository.builder()
            .id(UUID.randomUUID())
            .installation(installation)
            .name("core-service")
            .fullName("octocat/core-service")
            .build();

        pullRequest = PullRequest.builder()
            .id(UUID.randomUUID())
            .repository(repository)
            .prNumber(101)
            .title("feat: order service revamp")
            .baseSha("baseSha123")
            .headSha("headSha456")
            .status("open")
            .build();

        reviewRun = ReviewRun.builder()
            .id(runId)
            .pullRequest(pullRequest)
            .commitSha("headSha456")
            .status("queued")
            .build();
    }

    @Test
    @DisplayName("processReview should successfully execute end-to-end review and persist findings")
    void shouldExecuteSuccessfulReview() {
        when(reviewRunRepository.findById(runId)).thenReturn(Optional.of(reviewRun));
        when(reviewRunRepository.save(any(ReviewRun.class))).thenAnswer(inv -> inv.getArgument(0));

        // Active rules
        ArchitectureRule rule = ArchitectureRule.builder()
            .id(UUID.randomUUID())
            .repository(repository)
            .name("Layer Boundary")
            .isEnabled(true)
            .currentVersion(1)
            .build();
        RuleVersion ruleVersion = RuleVersion.builder()
            .id(UUID.randomUUID())
            .rule(rule)
            .versionNumber(1)
            .name("Layer Boundary")
            .boundary("Services must not import Controllers")
            .build();

        when(ruleRepository.findByRepositoryIdAndIsEnabledTrue(repository.getId())).thenReturn(List.of(rule));
        when(ruleVersionRepository.findByRuleIdAndVersionNumber(rule.getId(), 1)).thenReturn(Optional.of(ruleVersion));

        // Diff
        GitHubFileDiffDto fileDiff = GitHubFileDiffDto.builder()
            .filename("src/OrderService.java")
            .status("modified")
            .patch("@@ -10,3 +10,4 @@\n+import UserController;")
            .build();
        when(diffService.fetchCompareDiff("octocat/core-service", "baseSha123", "headSha456", 998877L))
            .thenReturn(List.of(fileDiff));

        // Gemini Findings
        GeminiFindingItem findingItem = GeminiFindingItem.builder()
            .category("architecture")
            .severity("high")
            .title("Illegal Controller Import")
            .summary("OrderService directly couples with UserController")
            .filePath("src/OrderService.java")
            .lineStart(11)
            .lineEnd(11)
            .codeExcerpt("import UserController;")
            .explanation("Violates layer boundaries")
            .suggestedCorrection("Use domain events")
            .ruleName("Layer Boundary")
            .build();

        when(geminiAiService.analyzeDiff(eq("octocat/core-service"), eq("feat: order service revamp"), any(), any()))
            .thenReturn(List.of(findingItem));

        ReviewRun result = workerService.processReview(runId);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("completed");
        assertThat(result.getStartedAt()).isNotNull();
        assertThat(result.getCompletedAt()).isNotNull();
        assertThat(result.getRuleVersions()).contains(ruleVersion);

        ArgumentCaptor<Finding> findingCaptor = ArgumentCaptor.forClass(Finding.class);
        verify(findingRepository).save(findingCaptor.capture());

        Finding savedFinding = findingCaptor.getValue();
        assertThat(savedFinding.getTitle()).isEqualTo("Illegal Controller Import");
        assertThat(savedFinding.getCategory()).isEqualTo("architecture");
        assertThat(savedFinding.getSeverity()).isEqualTo("high");
        assertThat(savedFinding.getRuleVersion()).isEqualTo(ruleVersion);
    }

    @Test
    @DisplayName("processReview should complete with 0 findings when diff contains no reviewable files")
    void shouldCompleteWhenZeroDiffFiles() {
        when(reviewRunRepository.findById(runId)).thenReturn(Optional.of(reviewRun));
        when(reviewRunRepository.save(any(ReviewRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ruleRepository.findByRepositoryIdAndIsEnabledTrue(repository.getId())).thenReturn(List.of());

        when(diffService.fetchCompareDiff("octocat/core-service", "baseSha123", "headSha456", 998877L))
            .thenReturn(List.of());

        ReviewRun result = workerService.processReview(runId);

        assertThat(result.getStatus()).isEqualTo("completed");
        verifyNoInteractions(geminiAiService);
        verifyNoInteractions(findingRepository);
    }

    @Test
    @DisplayName("processReview should set status failed when exception occurs during review")
    void shouldSetStatusFailedOnException() {
        when(reviewRunRepository.findById(runId)).thenReturn(Optional.of(reviewRun));
        when(reviewRunRepository.save(any(ReviewRun.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ruleRepository.findByRepositoryIdAndIsEnabledTrue(repository.getId())).thenReturn(List.of());

        when(diffService.fetchCompareDiff(any(), any(), any(), any()))
            .thenThrow(new RuntimeException("GitHub API network timeout"));

        ReviewRun result = workerService.processReview(runId);

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getFailureReason()).contains("GitHub API network timeout");
    }
}
