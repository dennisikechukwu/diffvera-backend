package me.dennislab.diffvera.diff;

import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.common.error.ExternalApiException;
import me.dennislab.diffvera.diff.dto.GitHubCompareResponse;
import me.dennislab.diffvera.diff.dto.GitHubFileDiffDto;
import me.dennislab.diffvera.installation.github.GitHubAppAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service that integrates with GitHub's Compare API to fetch
 * and sanitize unified git diffs between two commit SHAs or branch refs.
 */
@Service
@Slf4j
public class GitHubDiffService {

    private final GitHubAppAuthService appAuthService;
    private final DiffSanitizerService diffSanitizerService;
    private final RestClient restClient;

    public GitHubDiffService(
        GitHubAppAuthService appAuthService,
        DiffSanitizerService diffSanitizerService
    ) {
        this(appAuthService, diffSanitizerService, RestClient.builder().build());
    }

    @Autowired
    public GitHubDiffService(
        GitHubAppAuthService appAuthService,
        DiffSanitizerService diffSanitizerService,
        RestClient restClient
    ) {
        this.appAuthService = appAuthService;
        this.diffSanitizerService = diffSanitizerService;
        this.restClient = restClient != null ? restClient : RestClient.builder().build();
    }

    /**
     * Fetches and sanitizes changed files between base and head commit SHAs.
     */
    public List<GitHubFileDiffDto> fetchCompareDiff(
        String repoFullName,
        String baseSha,
        String headSha,
        Long installationId
    ) {
        log.info("Fetching diff for repository {} [{}...{}] via installation {}", repoFullName, baseSha, headSha, installationId);

        Optional<String> tokenOpt = appAuthService.getInstallationToken(installationId);
        if (tokenOpt.isEmpty()) {
            log.warn("Unable to obtain installation access token for installation ID {}. GitHub App may be unconfigured.", installationId);
            return Collections.emptyList();
        }

        try {
            String token = tokenOpt.get();
            String uri = String.format("https://api.github.com/repos/%s/compare/%s...%s", repoFullName, baseSha, headSha);

            GitHubCompareResponse response = restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("User-Agent", "Diffvera-Backend")
                .retrieve()
                .body(GitHubCompareResponse.class);

            if (response == null || response.getFiles() == null) {
                log.warn("Received empty response from GitHub Compare API for {} [{}...{}]", repoFullName, baseSha, headSha);
                return Collections.emptyList();
            }

            log.info("GitHub returned {} raw changed files for {}. Sanitizing...", response.getFiles().size(), repoFullName);
            List<GitHubFileDiffDto> sanitized = diffSanitizerService.sanitizeAndFilter(response.getFiles());
            log.info("Retained {} reviewable files after filtering.", sanitized.size());
            return sanitized;

        } catch (Exception e) {
            log.error("Failed to query GitHub Compare API for {} [{}...{}]: {}", repoFullName, baseSha, headSha, e.getMessage(), e);
            throw new ExternalApiException("GitHub Compare API", e.getMessage());
        }
    }
}
