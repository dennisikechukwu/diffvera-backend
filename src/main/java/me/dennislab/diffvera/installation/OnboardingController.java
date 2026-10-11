package me.dennislab.diffvera.installation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.installation.dto.OnboardingStatusDto;
import me.dennislab.diffvera.repository.GitRepoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing onboarding and installation setup status.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding", description = "GitHub App onboarding and installation status")
@Slf4j
public class OnboardingController {

    private final InstallationRepository installationRepository;
    private final GitRepoRepository gitRepoRepository;
    private final me.dennislab.diffvera.user.CurrentUserService currentUserService;
    private final me.dennislab.diffvera.installation.github.GitHubAppAuthService gitHubAppAuthService;
    private final me.dennislab.diffvera.repository.RepositorySyncService repositorySyncService;
    private final String appSlug;

    public OnboardingController(
        InstallationRepository installationRepository,
        GitRepoRepository gitRepoRepository,
        @Value("${diffvera.github.app-slug:diffvera}") String appSlug
    ) {
        this(installationRepository, gitRepoRepository, null, null, null, appSlug);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OnboardingController(
        InstallationRepository installationRepository,
        GitRepoRepository gitRepoRepository,
        me.dennislab.diffvera.user.CurrentUserService currentUserService,
        me.dennislab.diffvera.installation.github.GitHubAppAuthService gitHubAppAuthService,
        me.dennislab.diffvera.repository.RepositorySyncService repositorySyncService,
        @Value("${diffvera.github.app-slug:diffvera}") String appSlug
    ) {
        this.installationRepository = installationRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.currentUserService = currentUserService;
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.repositorySyncService = repositorySyncService;
        this.appSlug = appSlug;
    }

    @Operation(summary = "Check GitHub App installation status", description = "Returns whether the user has installed the GitHub App and provides the installation URL if needed.")
    @ApiResponse(responseCode = "200", description = "Current onboarding status",
        content = @Content(schema = @Schema(implementation = OnboardingStatusDto.class)))
    @GetMapping("/status")
    public ResponseEntity<OnboardingStatusDto> getOnboardingStatus(
        @org.springframework.web.bind.annotation.RequestParam(value = "installation_id", required = false) Long installationId,
        @org.springframework.web.bind.annotation.RequestParam(value = "installationId", required = false) Long installationIdCamel
    ) {
        Long targetInstallId = installationId != null ? installationId : installationIdCamel;

        // If a new installation was just completed and redirected back with installation_id
        if (targetInstallId != null && gitHubAppAuthService != null && repositorySyncService != null) {
            gitHubAppAuthService.getInstallationDetail(targetInstallId).ifPresent(detail -> {
                String accountLogin = detail.account() != null ? detail.account().login() : null;
                String accountType = detail.account() != null ? detail.account().type() : "User";
                if (accountLogin != null) {
                    Installation inst = installationRepository.findByGithubInstallationId(targetInstallId)
                        .orElseGet(() -> Installation.builder()
                            .githubInstallationId(targetInstallId)
                            .accountLogin(accountLogin)
                            .accountType(accountType)
                            .status("connected")
                            .build());
                    inst.setAccountLogin(accountLogin);
                    inst.setAccountType(accountType);
                    inst.setStatus("connected");
                    Installation saved = installationRepository.save(inst);
                    repositorySyncService.syncInstallation(saved);
                }
            });
        }

        int installationCount;
        int repositoryCount;

        if (currentUserService != null) {
            me.dennislab.diffvera.user.User user = currentUserService.getCurrentUserOrDevFallback();
            String userLogin = user != null ? user.getLogin() : "";
            installationCount = (int) installationRepository.countByAccountLoginIgnoreCase(userLogin);
            repositoryCount = (int) gitRepoRepository.countAccessibleRepositories(userLogin);
            if (installationCount == 0 && repositorySyncService != null) {
                repositorySyncService.discoverAndSyncInstallations();
                installationCount = (int) installationRepository.countByAccountLoginIgnoreCase(userLogin);
                repositoryCount = (int) gitRepoRepository.countAccessibleRepositories(userLogin);
            }
        } else {
            installationCount = (int) installationRepository.count();
            repositoryCount = (int) gitRepoRepository.count();
        }

        String status = (installationCount > 0 && repositoryCount > 0) ? "ready" : "needs_installation";
        String installUrl = String.format("https://github.com/apps/%s/installations/new", appSlug);

        OnboardingStatusDto dto = OnboardingStatusDto.builder()
            .status(status)
            .installationCount(installationCount)
            .repositoryCount(repositoryCount)
            .installUrl(installUrl)
            .build();

        return ResponseEntity.ok(dto);
    }
}
