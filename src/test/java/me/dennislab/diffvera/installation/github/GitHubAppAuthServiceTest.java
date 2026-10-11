package me.dennislab.diffvera.installation.github;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GitHubAppAuthServiceTest {

    @Test
    @DisplayName("Should detect unconfigured state when app ID and private key are missing")
    void shouldHandleUnconfiguredState() {
        GitHubAppAuthService service = new GitHubAppAuthService("", "", RestClient.builder().build());

        assertFalse(service.isConfigured());
        assertEquals(Optional.empty(), service.createGitHubAppJwt());
        assertEquals(Optional.empty(), service.getInstallationToken(12345L));
    }
}
