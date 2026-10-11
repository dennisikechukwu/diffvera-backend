package me.dennislab.diffvera.review;

import me.dennislab.diffvera.common.error.BadRequestException;
import me.dennislab.diffvera.diff.GitHubDiffService;
import me.dennislab.diffvera.pullrequest.PullRequest;
import me.dennislab.diffvera.pullrequest.PullRequestRepository;
import me.dennislab.diffvera.repository.Repository;
import me.dennislab.diffvera.review.dto.FindingFeedbackRequestDto;
import me.dennislab.diffvera.review.dto.FindingResponseDto;
import me.dennislab.diffvera.review.dto.ReviewDetailDto;
import me.dennislab.diffvera.review.dto.ReviewSummaryDto;
import me.dennislab.diffvera.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRunRepository reviewRunRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private FindingRepository findingRepository;

    @Mock
    private FindingFeedbackRepository findingFeedbackRepository;

    @Mock
    private GitHubDiffService diffService;

    @Mock
    private ReviewWorkerService reviewWorkerService;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
            reviewRunRepository,
            pullRequestRepository,
            findingRepository,
            findingFeedbackRepository,
            diffService,
            reviewWorkerService
        );
    }

    @Test
    @DisplayName("getReviewById should return full review details with findings and files")
    void shouldReturnReviewDetails() {
        UUID reviewId = UUID.randomUUID();
        Repository repo = Repository.builder().id(UUID.randomUUID()).fullName("northstar/order-service").build();
        PullRequest pr = PullRequest.builder().id(UUID.randomUUID()).repository(repo).prNumber(42).title("PR 42").baseSha("base").headSha("head").build();
        ReviewRun run = ReviewRun.builder().id(reviewId).pullRequest(pr).commitSha("head").status("completed").requestedAt(OffsetDateTime.now()).build();

        Finding finding = Finding.builder()
            .id(UUID.randomUUID())
            .reviewRun(run)
            .category("security")
            .severity("high")
            .title("Security issue")
            .summary("Summary")
            .filePath("src/index.ts")
            .lineStart(10)
            .lineEnd(15)
            .codeExcerpt("foo()")
            .explanation("explanation")
            .build();

        when(reviewRunRepository.findById(reviewId)).thenReturn(Optional.of(run));
        when(findingRepository.findByReviewRunId(reviewId)).thenReturn(List.of(finding));
        when(findingFeedbackRepository.findByFindingId(finding.getId())).thenReturn(Optional.empty());
        when(diffService.fetchCompareDiff(any(), any(), any(), any())).thenReturn(List.of());

        ReviewDetailDto detail = reviewService.getReviewById(reviewId);

        assertThat(detail.getId()).isEqualTo(reviewId);
        assertThat(detail.getPullRequestNumber()).isEqualTo(42);
        assertThat(detail.getStatus()).isEqualTo("completed");
        assertThat(detail.getFindings()).hasSize(1);
        assertThat(detail.getFindings().get(0).getTitle()).isEqualTo("Security issue");
    }

    @Test
    @DisplayName("triggerReview should reuse existing in-flight review if queued or running")
    void shouldReuseExistingInFlightReview() {
        UUID prId = UUID.randomUUID();
        Repository repo = Repository.builder().id(UUID.randomUUID()).fullName("northstar/order-service").build();
        PullRequest pr = PullRequest.builder().id(prId).repository(repo).prNumber(42).headSha("head123").build();
        ReviewRun existing = ReviewRun.builder().id(UUID.randomUUID()).pullRequest(pr).commitSha("head123").status("running").build();

        when(pullRequestRepository.findById(prId)).thenReturn(Optional.of(pr));
        when(reviewRunRepository.findByPullRequestIdAndCommitShaAndStatusIn(prId, "head123", List.of("queued", "running")))
            .thenReturn(Optional.of(existing));
        when(reviewRunRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        User user = User.builder().id(UUID.randomUUID()).login("test-dev").build();
        ReviewDetailDto result = reviewService.triggerReview(prId, user);

        assertThat(result.getId()).isEqualTo(existing.getId());
        verify(reviewWorkerService, never()).processReviewAsync(any());
    }

    @Test
    @DisplayName("triggerReview should create new review run and dispatch worker when none in flight")
    void shouldCreateAndDispatchNewReview() {
        UUID prId = UUID.randomUUID();
        Repository repo = Repository.builder().id(UUID.randomUUID()).fullName("northstar/order-service").build();
        PullRequest pr = PullRequest.builder().id(prId).repository(repo).prNumber(42).headSha("head123").build();

        when(pullRequestRepository.findById(prId)).thenReturn(Optional.of(pr));
        when(reviewRunRepository.findByPullRequestIdAndCommitShaAndStatusIn(prId, "head123", List.of("queued", "running")))
            .thenReturn(Optional.empty());

        UUID newReviewId = UUID.randomUUID();
        ReviewRun newRun = ReviewRun.builder().id(newReviewId).pullRequest(pr).commitSha("head123").status("queued").build();
        when(reviewRunRepository.save(any(ReviewRun.class))).thenReturn(newRun);
        when(reviewRunRepository.findById(newReviewId)).thenReturn(Optional.of(newRun));

        User user = User.builder().id(UUID.randomUUID()).login("test-dev").build();
        ReviewDetailDto result = reviewService.triggerReview(prId, user);

        assertThat(result.getId()).isEqualTo(newReviewId);
        assertThat(result.getStatus()).isEqualTo("queued");
        verify(reviewWorkerService).processReviewAsync(newReviewId);
    }

    @Test
    @DisplayName("submitFindingFeedback should record feedback and normalize false-positive")
    void shouldSubmitFindingFeedback() {
        UUID findingId = UUID.randomUUID();
        ReviewRun run = ReviewRun.builder().id(UUID.randomUUID()).build();
        Finding finding = Finding.builder().id(findingId).reviewRun(run).category("correctness").severity("medium").title("Issue").summary("S").filePath("f").lineStart(1).lineEnd(2).build();

        when(findingRepository.findById(findingId)).thenReturn(Optional.of(finding));

        User user = User.builder().id(UUID.randomUUID()).login("dev").build();
        when(findingFeedbackRepository.findByFindingIdAndUserId(findingId, user.getId())).thenReturn(Optional.empty());

        FindingFeedbackRequestDto req = FindingFeedbackRequestDto.builder().feedback("false-positive").build();
        FindingResponseDto updated = reviewService.submitFindingFeedback(findingId, req, user);

        assertThat(updated.getFeedback()).isEqualTo("false-positive");

        ArgumentCaptor<FindingFeedback> captor = ArgumentCaptor.forClass(FindingFeedback.class);
        verify(findingFeedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getFeedback()).isEqualTo("false_positive");
    }

    @Test
    @DisplayName("submitFindingFeedback should throw BadRequestException for invalid feedback")
    void shouldRejectInvalidFeedback() {
        UUID findingId = UUID.randomUUID();
        Finding finding = Finding.builder().id(findingId).build();
        when(findingRepository.findById(findingId)).thenReturn(Optional.of(finding));

        User user = User.builder().id(UUID.randomUUID()).login("dev").build();
        FindingFeedbackRequestDto req = FindingFeedbackRequestDto.builder().feedback("dislike").build();

        assertThatThrownBy(() -> reviewService.submitFindingFeedback(findingId, req, user))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("getRecentReviews should return reviews with counts")
    void shouldReturnRecentReviews() {
        UUID runId = UUID.randomUUID();
        Repository repo = Repository.builder().id(UUID.randomUUID()).fullName("northstar/order-service").build();
        PullRequest pr = PullRequest.builder().id(UUID.randomUUID()).repository(repo).prNumber(10).title("Fix").build();
        ReviewRun run = ReviewRun.builder().id(runId).pullRequest(pr).commitSha("sha").status("completed").build();

        when(reviewRunRepository.findAllByOrderByRequestedAtDesc()).thenReturn(List.of(run));
        when(findingRepository.countByReviewRunId(runId)).thenReturn(2L);
        when(findingRepository.countByReviewRunIdAndSeverity(runId, "high")).thenReturn(1L);

        List<ReviewSummaryDto> list = reviewService.getRecentReviews(null, null, 10);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getFindingCount()).isEqualTo(2);
        assertThat(list.get(0).getHighSeverityCount()).isEqualTo(1);
    }
}
