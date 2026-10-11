package me.dennislab.diffvera.auth;

import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.auth.dto.GitHubTokenResponse;
import me.dennislab.diffvera.auth.dto.GitHubUserResponse;
import me.dennislab.diffvera.auth.dto.UserSessionDto;
import me.dennislab.diffvera.user.User;
import me.dennislab.diffvera.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing GitHub OAuth 2.0 authentication exchange, user account
 * synchronization, and session conversion.
 */
@Service
@Slf4j
public class AuthService {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final UserRepository userRepository;
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
        @Value("${diffvera.github.client-id:}") String clientId,
        @Value("${diffvera.github.client-secret:}") String clientSecret,
        @Value("${diffvera.github.redirect-uri:http://localhost:8080/api/v1/auth/callback/github}") String redirectUri,
        UserRepository userRepository
    ) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.redirectUri = redirectUri.trim();
        this.userRepository = userRepository;
        this.restClient = RestClient.builder().build();
    }

    public AuthService(
        String clientId,
        String clientSecret,
        String redirectUri,
        UserRepository userRepository,
        RestClient restClient
    ) {
        this.clientId = clientId.trim();
        this.clientSecret = clientSecret.trim();
        this.redirectUri = redirectUri.trim();
        this.userRepository = userRepository;
        this.restClient = restClient != null ? restClient : RestClient.builder().build();
    }

    /**
     * Constructs the GitHub OAuth authorization URL redirecting the browser to GitHub's consent screen.
     */
    public String buildGitHubAuthorizationUrl() {
        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", "read:user,user:email")
            .encode()
            .toUriString();
    }

    /**
     * Handles the callback code from GitHub, exchanges it for an access token,
     * queries the authenticated GitHub user profile, and persists the user.
     */
    @Transactional
    public User processGitHubCallback(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Authorization code must not be null or blank");
        }

        log.info("Exchanging GitHub OAuth code with client ID: {}", clientId);

        // 1. Exchange authorization code for GitHub access token
        GitHubTokenResponse tokenResponse = restClient.post()
            .uri("https://github.com/login/oauth/access_token")
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .body(Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code,
                "redirect_uri", redirectUri
            ))
            .retrieve()
            .body(GitHubTokenResponse.class);

        if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
            String errorMsg = tokenResponse != null && tokenResponse.getErrorDescription() != null
                ? tokenResponse.getErrorDescription()
                : "Failed to obtain access token from GitHub";
            log.error("GitHub OAuth token exchange failed: {}", errorMsg);
            throw new IllegalStateException("GitHub OAuth error: " + errorMsg);
        }

        // 2. Query GitHub user profile using the access token
        GitHubUserResponse gitHubUser = restClient.get()
            .uri("https://api.github.com/user")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResponse.getAccessToken())
            .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .header("User-Agent", "Diffvera-Backend")
            .retrieve()
            .body(GitHubUserResponse.class);

        if (gitHubUser == null || gitHubUser.getId() == null) {
            throw new IllegalStateException("Failed to retrieve user profile from GitHub API");
        }

        // 3. Upsert into database
        User user = userRepository.findByGithubUserId(gitHubUser.getId())
            .orElseGet(() -> User.builder().githubUserId(gitHubUser.getId()).build());

        user.setLogin(gitHubUser.getLogin());
        user.setName(gitHubUser.getName() != null && !gitHubUser.getName().isBlank()
            ? gitHubUser.getName()
            : gitHubUser.getLogin());
        user.setEmail(gitHubUser.getEmail());
        user.setAvatarUrl(gitHubUser.getAvatarUrl());

        return userRepository.save(user);
    }

    /**
     * Resolves an authenticated user by their primary UUID.
     */
    @Transactional(readOnly = true)
    public Optional<User> findUserById(UUID userId) {
        return userRepository.findById(userId);
    }

    /**
     * Converts a domain User entity into a UserSessionDto.
     */
    public UserSessionDto toSessionDto(User user) {
        return UserSessionDto.builder()
            .id(user.getId())
            .githubUserId(user.getGithubUserId())
            .login(user.getLogin())
            .name(user.getName())
            .email(user.getEmail())
            .avatarUrl(user.getAvatarUrl())
            .build();
    }
}
