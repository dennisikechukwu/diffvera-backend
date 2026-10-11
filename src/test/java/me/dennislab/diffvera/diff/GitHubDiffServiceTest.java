package me.dennislab.diffvera.diff;

import me.dennislab.diffvera.common.error.ExternalApiException;
import me.dennislab.diffvera.diff.dto.GitHubCompareResponse;
import me.dennislab.diffvera.diff.dto.GitHubFileDiffDto;
import me.dennislab.diffvera.installation.github.GitHubAppAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubDiffServiceTest {

    @Mock
    private GitHubAppAuthService appAuthService;

    @Mock
    private DiffSanitizerService sanitizerService;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private GitHubDiffService diffService;

    @BeforeEach
    void setUp() {
        diffService = new GitHubDiffService(appAuthService, sanitizerService, restClient);
    }

    @Test
    @DisplayName("fetchCompareDiff should return empty list if installation token cannot be minted")
    void shouldReturnEmptyWhenTokenUnavailable() {
        when(appAuthService.getInstallationToken(12345L)).thenReturn(Optional.empty());

        List<GitHubFileDiffDto> result = diffService.fetchCompareDiff("owner/repo", "base123", "head456", 12345L);

        assertThat(result).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("fetchCompareDiff should retrieve from GitHub Compare API and pass through sanitizer")
    void shouldFetchAndSanitizeDiff() {
        when(appAuthService.getInstallationToken(12345L)).thenReturn(Optional.of("inst_token_abc"));

        GitHubFileDiffDto rawFile = GitHubFileDiffDto.builder()
            .filename("src/App.java")
            .status("modified")
            .patch("@@ -1 +1 @@")
            .build();
        GitHubCompareResponse compareResponse = GitHubCompareResponse.builder()
            .status("ahead")
            .aheadBy(1)
            .files(List.of(rawFile))
            .build();

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(String.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(GitHubCompareResponse.class)).thenReturn(compareResponse);

        when(sanitizerService.sanitizeAndFilter(List.of(rawFile))).thenReturn(List.of(rawFile));

        List<GitHubFileDiffDto> result = diffService.fetchCompareDiff("owner/repo", "baseSha", "headSha", 12345L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFilename()).isEqualTo("src/App.java");
        verify(sanitizerService).sanitizeAndFilter(List.of(rawFile));
    }

    @Test
    @DisplayName("fetchCompareDiff should throw ExternalApiException if HTTP call fails")
    void shouldThrowExternalApiExceptionOnError() {
        when(appAuthService.getInstallationToken(12345L)).thenReturn(Optional.of("inst_token_abc"));
        when(restClient.get()).thenThrow(new RuntimeException("GitHub 503 Service Unavailable"));

        assertThatThrownBy(() -> diffService.fetchCompareDiff("owner/repo", "baseSha", "headSha", 12345L))
            .isInstanceOf(ExternalApiException.class)
            .hasMessageContaining("GitHub Compare API");
    }
}
