package me.dennislab.diffvera.auth;

import me.dennislab.diffvera.auth.dto.UserSessionDto;
import me.dennislab.diffvera.user.User;
import me.dennislab.diffvera.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            "dummy-client-id",
            "dummy-client-secret",
            "http://localhost:8080/api/v1/auth/callback/github",
            userRepository,
            RestClient.builder().build()
        );
    }

    @Test
    @DisplayName("Should build valid GitHub OAuth authorization URL")
    void shouldBuildAuthorizationUrl() {
        String url = authService.buildGitHubAuthorizationUrl();
        assertTrue(url.startsWith("https://github.com/login/oauth/authorize"));
        assertTrue(url.contains("client_id=dummy-client-id"));
        assertTrue(url.contains("redirect_uri="));
        assertTrue(url.contains("scope="));
    }

    @Test
    @DisplayName("Should correctly map User entity to UserSessionDto")
    void shouldMapUserToSessionDto() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .githubUserId(583231L)
            .login("octocat")
            .name("Monalisa Octocat")
            .email("octocat@github.com")
            .avatarUrl("https://avatars.githubusercontent.com/u/583231")
            .build();

        UserSessionDto dto = authService.toSessionDto(user);
        assertNotNull(dto);
        assertEquals(userId, dto.getId());
        assertEquals(583231L, dto.getGithubUserId());
        assertEquals("octocat", dto.getLogin());
        assertEquals("Monalisa Octocat", dto.getName());
        assertEquals("octocat@github.com", dto.getEmail());
        assertEquals("https://avatars.githubusercontent.com/u/583231", dto.getAvatarUrl());
    }
}
