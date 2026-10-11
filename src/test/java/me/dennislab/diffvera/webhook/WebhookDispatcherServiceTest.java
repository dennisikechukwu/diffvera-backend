package me.dennislab.diffvera.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.dennislab.diffvera.installation.Installation;
import me.dennislab.diffvera.installation.InstallationRepository;
import me.dennislab.diffvera.pullrequest.PullRequest;
import me.dennislab.diffvera.pullrequest.PullRequestRepository;
import me.dennislab.diffvera.repository.GitRepoRepository;
import me.dennislab.diffvera.repository.Repository;
import me.dennislab.diffvera.review.ReviewRun;
import me.dennislab.diffvera.review.ReviewRunRepository;
import me.dennislab.diffvera.webhook.dto.WebhookResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookDispatcherServiceTest {

    @Mock
    private InstallationRepository installationRepository;

    @Mock
    private GitRepoRepository gitRepoRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private ReviewRunRepository reviewRunRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebhookDispatcherService dispatcherService;

    @BeforeEach
    void setUp() {
        dispatcherService = new WebhookDispatcherService(
            installationRepository,
            gitRepoRepository,
            pullRequestRepository,
            reviewRunRepository,
            objectMapper
        );
    }

    @Test
    @DisplayName("Should process ping event and return PONG")
    void shouldHandlePingEvent() {
        WebhookResponseDto result = dispatcherService.dispatch("ping", "delivery-1", "{}");

        assertEquals("processed", result.getStatus());
        assertEquals("ping", result.getEvent());
        assertEquals("PONG", result.getMessage());
    }

    @Test
    @DisplayName("Should process pull_request opened, persist PR, and enqueue review run")
    void shouldHandlePullRequestOpened() {
        String payload = """
            {
              "action": "opened",
              "pull_request": {
                "id": 1001,
                "number": 42,
                "title": "Add architecture boundary rules",
                "user": { "login": "dennisikechukwu" },
                "base": { "ref": "main", "sha": "base123456789012345678901234567890123456" },
                "head": { "ref": "feat/rules", "sha": "head123456789012345678901234567890123456" },
                "additions": 150,
                "deletions": 20
              },
              "repository": {
                "id": 999,
                "name": "diffvera",
                "full_name": "dennisikechukwu/diffvera",
                "private": false,
                "default_branch": "main"
              },
              "installation": {
                "id": 888,
                "account": { "login": "dennisikechukwu", "type": "User" }
              }
            }
            """;

        Installation mockInst = Installation.builder().id(UUID.randomUUID()).githubInstallationId(888L).build();
        Repository mockRepo = Repository.builder().id(UUID.randomUUID()).githubRepoId(999L).build();
        PullRequest mockPr = PullRequest.builder().id(UUID.randomUUID()).repository(mockRepo).prNumber(42).build();

        when(installationRepository.findByGithubInstallationId(888L)).thenReturn(Optional.of(mockInst));
        when(gitRepoRepository.findByGithubRepoId(999L)).thenReturn(Optional.of(mockRepo));
        when(pullRequestRepository.findByRepositoryIdAndPrNumber(mockRepo.getId(), 42)).thenReturn(Optional.empty());
        when(pullRequestRepository.save(any(PullRequest.class))).thenReturn(mockPr);
        when(reviewRunRepository.findByPullRequestIdAndCommitShaAndStatusIn(any(), any(), any())).thenReturn(Optional.empty());

        WebhookResponseDto response = dispatcherService.dispatch("pull_request", "delivery-42", payload);

        assertEquals("processed", response.getStatus());
        assertEquals("pull_request", response.getEvent());
        assertTrue(response.getMessage().contains("queued review"));
        verify(pullRequestRepository).save(any(PullRequest.class));
        verify(reviewRunRepository).save(any(ReviewRun.class));
    }

    @Test
    @DisplayName("Should mark pull request closed when action is closed")
    void shouldHandlePullRequestClosed() {
        String payload = """
            {
              "action": "closed",
              "pull_request": {
                "number": 42,
                "head": { "sha": "sha123" },
                "base": { "sha": "sha456" }
              },
              "repository": { "id": 999 },
              "installation": { "id": 888 }
            }
            """;

        Installation mockInst = Installation.builder().id(UUID.randomUUID()).githubInstallationId(888L).build();
        Repository mockRepo = Repository.builder().id(UUID.randomUUID()).githubRepoId(999L).build();
        PullRequest mockPr = PullRequest.builder().id(UUID.randomUUID()).repository(mockRepo).prNumber(42).status("open").build();

        when(installationRepository.findByGithubInstallationId(888L)).thenReturn(Optional.of(mockInst));
        when(gitRepoRepository.findByGithubRepoId(999L)).thenReturn(Optional.of(mockRepo));
        when(pullRequestRepository.findByRepositoryIdAndPrNumber(mockRepo.getId(), 42)).thenReturn(Optional.of(mockPr));

        WebhookResponseDto response = dispatcherService.dispatch("pull_request", "delivery-close", payload);

        assertEquals("processed", response.getStatus());
        assertTrue(response.getMessage().contains("closed"));
        assertEquals("closed", mockPr.getStatus());
        verify(pullRequestRepository).save(mockPr);
        verify(reviewRunRepository, never()).save(any(ReviewRun.class));
    }
}
