package me.dennislab.diffvera.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.dennislab.diffvera.auth.dto.UserSessionDto;
import me.dennislab.diffvera.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    private JwtService jwtService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("diffvera-super-secret-jwt-key-minimum-256-bits-length-for-hmac-sha!", 604800);
        authController = new AuthController(
            authService,
            jwtService,
            "diffvera_session",
            604800,
            "http://localhost:3000/dashboard"
        );
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/auth/github should redirect to GitHub consent URL")
    void shouldRedirectToGitHub() throws Exception {
        when(authService.buildGitHubAuthorizationUrl())
            .thenReturn("https://github.com/login/oauth/authorize?client_id=test");

        mockMvc.perform(get("/api/v1/auth/github"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("https://github.com/login/oauth/authorize?client_id=test"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/callback/github should exchange code, set session cookie, and redirect to dashboard")
    void shouldHandleGitHubCallback() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .login("octocat")
            .githubUserId(583231L)
            .email("octocat@github.com")
            .build();

        when(authService.processGitHubCallback("github-code-123")).thenReturn(user);

        mockMvc.perform(get("/api/v1/auth/callback/github").param("code", "github-code-123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:3000/dashboard"))
            .andExpect(cookie().exists("diffvera_session"))
            .andExpect(cookie().httpOnly("diffvera_session", true));
    }

    @Test
    @DisplayName("GET /api/v1/auth/session should return 401 Unauthorized when no authenticated principal")
    void shouldReturn401WithoutSessionCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/auth/session should return 200 OK when authenticated user principal exists")
    void shouldReturn200WithAuthenticatedPrincipal() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .login("dennis")
            .email("dennis@diffvera.com")
            .build();

        UserSessionDto sessionDto = UserSessionDto.builder()
            .id(userId)
            .login("dennis")
            .email("dennis@diffvera.com")
            .build();

        when(authService.toSessionDto(user)).thenReturn(sessionDto);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, List.of())
        );

        mockMvc.perform(get("/api/v1/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.login").value("dennis"))
            .andExpect(jsonPath("$.email").value("dennis@diffvera.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/sign-out should clear session cookie with Max-Age 0")
    void shouldClearSessionCookieOnSignOut() throws Exception {
        mockMvc.perform(post("/api/v1/auth/sign-out"))
            .andExpect(status().isNoContent())
            .andExpect(cookie().exists("diffvera_session"))
            .andExpect(cookie().maxAge("diffvera_session", 0));
    }
}
