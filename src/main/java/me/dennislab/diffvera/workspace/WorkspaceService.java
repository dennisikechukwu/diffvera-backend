package me.dennislab.diffvera.workspace;

import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.pullrequest.PullRequest;
import me.dennislab.diffvera.pullrequest.PullRequestRepository;
import me.dennislab.diffvera.repository.GitRepoRepository;
import me.dennislab.diffvera.repository.Repository;
import me.dennislab.diffvera.review.ReviewRun;
import me.dennislab.diffvera.review.ReviewRunRepository;
import me.dennislab.diffvera.review.ReviewService;
import me.dennislab.diffvera.review.dto.FindingResponseDto;
import me.dennislab.diffvera.review.dto.ReviewDetailDto;
import me.dennislab.diffvera.rule.ArchitectureRule;
import me.dennislab.diffvera.rule.ArchitectureRuleRepository;
import me.dennislab.diffvera.rule.RuleVersion;
import me.dennislab.diffvera.rule.RuleVersionRepository;
import me.dennislab.diffvera.user.User;
import me.dennislab.diffvera.workspace.dto.WorkspaceSnapshotDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service providing aggregated workspace state snapshots for the Diffvera frontend.
 */
@Service
@Slf4j
public class WorkspaceService {

    private final GitRepoRepository gitRepoRepository;
    private final PullRequestRepository pullRequestRepository;
    private final ReviewRunRepository reviewRunRepository;
    private final ReviewService reviewService;
    private final ArchitectureRuleRepository architectureRuleRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final me.dennislab.diffvera.repository.RepositorySyncService repositorySyncService;
    private me.dennislab.diffvera.pullrequest.PullRequestService pullRequestService;

    @org.springframework.beans.factory.annotation.Autowired
    public void setPullRequestService(me.dennislab.diffvera.pullrequest.PullRequestService pullRequestService) {
        this.pullRequestService = pullRequestService;
    }

    public WorkspaceService(
        GitRepoRepository gitRepoRepository,
        PullRequestRepository pullRequestRepository,
        ReviewRunRepository reviewRunRepository,
        ReviewService reviewService,
        ArchitectureRuleRepository architectureRuleRepository,
        RuleVersionRepository ruleVersionRepository,
        me.dennislab.diffvera.repository.RepositorySyncService repositorySyncService
    ) {
        this.gitRepoRepository = gitRepoRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.reviewRunRepository = reviewRunRepository;
        this.reviewService = reviewService;
        this.architectureRuleRepository = architectureRuleRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.repositorySyncService = repositorySyncService;
    }

    /**
     * Builds and returns a complete unified snapshot of all accessible resources.
     */
    @Transactional
    public WorkspaceSnapshotDto getWorkspaceSnapshot(User user) {
        log.info("Generating unified WorkspaceSnapshot for user {}", user != null ? user.getLogin() : "anonymous");

        WorkspaceSnapshotDto.WorkspaceAccountDto account = WorkspaceSnapshotDto.WorkspaceAccountDto.builder()
            .id(user != null && user.getId() != null ? user.getId().toString() : "user-default")
            .login(user != null ? user.getLogin() : "developer")
            .name(user != null && user.getName() != null ? user.getName() : "Diffvera Developer")
            .avatarUrl(user != null ? user.getAvatarUrl() : null)
            .build();

        List<Repository> repos = (user != null && user.getLogin() != null)
            ? gitRepoRepository.findAccessibleRepositories(user.getLogin())
            : List.of();

        if (repos.isEmpty() && user != null && user.getLogin() != null && repositorySyncService != null) {
            repositorySyncService.discoverAndSyncInstallations();
            repos = gitRepoRepository.findAccessibleRepositories(user.getLogin());
        }

        List<WorkspaceSnapshotDto.WorkspaceRepositoryDto> repositoryDtos = repos.stream()
            .map(r -> WorkspaceSnapshotDto.WorkspaceRepositoryDto.builder()
                .id(r.getId().toString())
                .fullName(r.getFullName())
                .language(r.getLanguage() != null ? r.getLanguage() : "TypeScript")
                .visibility(Boolean.TRUE.equals(r.getIsPrivate()) ? "private" : "public")
                .description(r.getName() + " service repository")
                .access(r.getAccessStatus() != null ? r.getAccessStatus().toLowerCase() : "available")
                .defaultBranch(r.getDefaultBranch() != null ? r.getDefaultBranch() : "main")
                .updatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null)
                .pushedAt(r.getPushedAt() != null ? r.getPushedAt().toString() : null)
                .build())
            .toList();

        List<UUID> repoIds = repos.stream().map(Repository::getId).toList();

        List<WorkspaceSnapshotDto.WorkspacePullRequestDto> pullRequestDtos = new ArrayList<>();
        List<WorkspaceSnapshotDto.WorkspaceReviewDto> reviewDtos = new ArrayList<>();
        List<WorkspaceSnapshotDto.WorkspaceRuleDto> ruleDtos = new ArrayList<>();

        if (!repoIds.isEmpty()) {
            List<PullRequest> prs = pullRequestRepository.findByRepositoryIdIn(repoIds);
            if (prs.isEmpty() && pullRequestService != null && !repos.isEmpty()) {
                log.info("No pull requests in database; syncing top active repositories for user {}", user != null ? user.getLogin() : "unknown");
                repos.stream().limit(3).forEach(r -> pullRequestService.syncPullRequestsFromGitHubIfConfigured(r.getId()));
                prs = pullRequestRepository.findByRepositoryIdIn(repoIds);
            }
            pullRequestDtos = prs.stream()
                .map(p -> WorkspaceSnapshotDto.WorkspacePullRequestDto.builder()
                    .id(p.getId().toString())
                    .repositoryId(p.getRepository().getId().toString())
                    .number(p.getPrNumber())
                    .title(p.getTitle())
                    .author(p.getAuthorLogin())
                    .status(p.getStatus() != null ? p.getStatus().toLowerCase() : "open")
                    .headSha(p.getHeadSha())
                    .branch(p.getHeadBranch())
                    .updatedAt(p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : "")
                    .additions(p.getAdditions() != null ? p.getAdditions() : 0)
                    .deletions(p.getDeletions() != null ? p.getDeletions() : 0)
                    .build())
                .toList();

            List<ReviewRun> reviewRuns = reviewRunRepository.findByPullRequestRepositoryIdInOrderByRequestedAtDesc(repoIds);
            for (ReviewRun run : reviewRuns) {
                Long requestedAtMillis = run.getRequestedAt() != null
                    ? run.getRequestedAt().toInstant().toEpochMilli()
                    : null;
                List<FindingResponseDto> findings = reviewService.getFindingsForReview(run.getId());

                reviewDtos.add(WorkspaceSnapshotDto.WorkspaceReviewDto.builder()
                    .id(run.getId().toString())
                    .pullRequestId(run.getPullRequest().getId().toString())
                    .headSha(run.getCommitSha())
                    .status(run.getStatus())
                    .createdAt(run.getRequestedAt() != null ? run.getRequestedAt().toString() : "")
                    .requestedAt(requestedAtMillis)
                    .findings(findings)
                    .files(List.of())
                    .build());
            }

            List<ArchitectureRule> rules = architectureRuleRepository.findByRepositoryIdIn(repoIds);
            for (ArchitectureRule rule : rules) {
                Optional<RuleVersion> rv = ruleVersionRepository.findByRuleIdAndVersionNumber(rule.getId(), rule.getCurrentVersion());
                ruleDtos.add(WorkspaceSnapshotDto.WorkspaceRuleDto.builder()
                    .id(rule.getId().toString())
                    .name(rule.getName())
                    .description(rv.map(RuleVersion::getBoundary).orElse(""))
                    .repositoryId(rule.getRepository().getId().toString())
                    .enabled(Boolean.TRUE.equals(rule.getIsEnabled()))
                    .build());
            }
        }

        return WorkspaceSnapshotDto.builder()
            .account(account)
            .repositories(repositoryDtos)
            .pullRequests(pullRequestDtos)
            .reviews(reviewDtos)
            .rules(ruleDtos)
            .build();
    }
}
