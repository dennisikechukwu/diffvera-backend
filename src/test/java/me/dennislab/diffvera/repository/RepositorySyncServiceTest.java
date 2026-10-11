package me.dennislab.diffvera.repository;

import me.dennislab.diffvera.installation.Installation;
import me.dennislab.diffvera.installation.InstallationRepository;
import me.dennislab.diffvera.installation.github.GitHubAppAuthService;
import me.dennislab.diffvera.installation.github.dto.GitHubRepoItemDto;
import me.dennislab.diffvera.pullrequest.PullRequestRepository;
import me.dennislab.diffvera.repository.dto.RepositoryDto;
import me.dennislab.diffvera.repository.dto.RepositorySyncResultDto;
import me.dennislab.diffvera.rule.ArchitectureRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositorySyncServiceTest {

    @Mock
    private InstallationRepository installationRepository;

    @Mock
    private GitRepoRepository gitRepoRepository;

    @Mock
    private GitHubAppAuthService gitHubAppAuthService;

    @Mock
    private ArchitectureRuleRepository architectureRuleRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    private RepositorySyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new RepositorySyncService(
            installationRepository,
            gitRepoRepository,
            gitHubAppAuthService,
            architectureRuleRepository,
            pullRequestRepository,
            RestClient.builder().build()
        );
    }

    @Test
    @DisplayName("Should return 0 synced when no installations exist")
    void shouldReturnZeroWhenNoInstallations() {
        when(installationRepository.findAll()).thenReturn(List.of());

        RepositorySyncResultDto result = syncService.syncAllInstallations();

        assertEquals("completed", result.getStatus());
        assertEquals(0, result.getSyncedRepositoriesCount());
        assertTrue(result.getMessage().contains("No active installations"));
    }

    @Test
    @DisplayName("Should correctly upsert a new repository")
    void shouldUpsertNewRepository() {
        Installation installation = Installation.builder()
            .id(UUID.randomUUID())
            .githubInstallationId(9876L)
            .accountLogin("acme-corp")
            .accountType("Organization")
            .build();

        GitHubRepoItemDto item = GitHubRepoItemDto.builder()
            .id(123456L)
            .name("payment-service")
            .fullName("acme-corp/payment-service")
            .isPrivate(true)
            .language("Java")
            .defaultBranch("main")
            .build();

        when(gitRepoRepository.findByGithubRepoId(123456L)).thenReturn(Optional.empty());
        when(gitRepoRepository.save(any(Repository.class))).thenAnswer(inv -> inv.getArgument(0));

        Repository saved = syncService.upsertRepository(installation, item);

        assertNotNull(saved);
        assertEquals(123456L, saved.getGithubRepoId());
        assertEquals("payment-service", saved.getName());
        assertEquals("acme-corp/payment-service", saved.getFullName());
        assertTrue(saved.getIsPrivate());
        assertEquals("Java", saved.getLanguage());
        assertEquals("available", saved.getAccessStatus());
        verify(gitRepoRepository).save(any(Repository.class));
    }

    @Test
    @DisplayName("Should correctly enrich Repository entity with rule and PR counts into DTO")
    void shouldMapEntityToDtoWithCounts() {
        UUID repoId = UUID.randomUUID();
        Repository repo = Repository.builder()
            .id(repoId)
            .githubRepoId(555L)
            .name("web-frontend")
            .fullName("acme-corp/web-frontend")
            .isPrivate(false)
            .language("TypeScript")
            .defaultBranch("main")
            .accessStatus("available")
            .build();

        when(architectureRuleRepository.countByRepositoryIdAndIsEnabledTrue(repoId)).thenReturn(4L);
        when(pullRequestRepository.countByRepositoryIdAndStatus(repoId, "open")).thenReturn(2L);

        RepositoryDto dto = syncService.toDto(repo);

        assertNotNull(dto);
        assertEquals(repoId, dto.getId());
        assertEquals("web-frontend", dto.getName());
        assertEquals(4L, dto.getActiveRuleCount());
        assertEquals(2L, dto.getOpenPrCount());
    }
}
