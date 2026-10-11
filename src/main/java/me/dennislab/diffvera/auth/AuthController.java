package me.dennislab.diffvera.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import me.dennislab.diffvera.auth.dto.UserSessionDto;
import me.dennislab.diffvera.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;

/**
 * REST controller for GitHub OAuth 2.0 authentication,
 * session status verification, and session sign-out.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "GitHub OAuth 2.0 authentication and session management")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final String cookieName;
    private final long expirationSeconds;
    private final String frontendDashboardUrl;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthController(
        AuthService authService,
        JwtService jwtService,
        @Value("${diffvera.jwt.cookie-name:diffvera_session}") String cookieName,
        @Value("${diffvera.jwt.expiration-seconds:604800}") long expirationSeconds,
        @Value("${diffvera.frontend.dashboard-url:http://localhost:3000/dashboard}") String frontendDashboardUrl,
        @Value("${diffvera.jwt.cookie-secure:false}") boolean cookieSecure,
        @Value("${diffvera.jwt.cookie-same-site:Lax}") String cookieSameSite
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookieName = cookieName;
        this.expirationSeconds = expirationSeconds;
        this.frontendDashboardUrl = frontendDashboardUrl;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = (cookieSameSite != null && !cookieSameSite.isBlank()) ? cookieSameSite.trim() : "Lax";
    }

    public AuthController(
        AuthService authService,
        JwtService jwtService,
        String cookieName,
        long expirationSeconds,
        String frontendDashboardUrl
    ) {
        this(authService, jwtService, cookieName, expirationSeconds, frontendDashboardUrl, false, "Lax");
    }

    @Operation(summary = "Initiate GitHub OAuth 2.0 flow", description = "Redirects browser to GitHub authorization consent screen.")
    @GetMapping("/github")
    public void initiateGitHubLogin(HttpServletResponse response) throws IOException {
        String url = authService.buildGitHubAuthorizationUrl();
        log.info("Redirecting user to GitHub authorization: {}", url);
        response.sendRedirect(url);
    }

    @Operation(summary = "GitHub OAuth 2.0 callback", description = "Exchanges authorization code for token, sets HTTP-only session cookie, and redirects to frontend dashboard.")
    @ApiResponses({
        @ApiResponse(responseCode = "302", description = "Redirect to frontend dashboard upon successful authentication"),
        @ApiResponse(responseCode = "400", description = "Missing or invalid authorization code")
    })
    @GetMapping("/callback/github")
    public void gitHubCallback(
        @Parameter(description = "Temporary authorization code provided by GitHub") @RequestParam("code") String code,
        HttpServletResponse response
    ) throws IOException {
        try {
            User user = authService.processGitHubCallback(code);
            String token = jwtService.generateToken(user);
            attachSessionCookie(response, token, expirationSeconds);
            log.info("GitHub authentication successful for user: {}", user.getLogin());
            response.sendRedirect(frontendDashboardUrl);
        } catch (Exception e) {
            log.warn("GitHub callback error: {}", e.getMessage());
            response.sendRedirect(frontendDashboardUrl);
        }
    }

    @Operation(summary = "Get current authenticated session profile", description = "Validates the HTTP-only JWT cookie and returns user profile. Returns 401 if unauthenticated.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated user profile",
            content = @Content(schema = @Schema(implementation = UserSessionDto.class))),
        @ApiResponse(responseCode = "401", description = "Not authenticated / invalid session cookie")
    })
    @GetMapping("/session")
    public ResponseEntity<UserSessionDto> getSession() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return ResponseEntity.ok(authService.toSessionDto(user));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @Operation(summary = "Sign out and clear session cookie", description = "Clears the diffvera_session cookie from the browser.")
    @ApiResponse(responseCode = "204", description = "Session cookie successfully cleared")
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(HttpServletResponse response) {
        attachSessionCookie(response, "", 0);
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /**
     * Helper to write an HTTP-only, SameSite=Lax cookie onto the response.
     */
    private void attachSessionCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        boolean isSecure = this.cookieSecure;
        if ("None".equalsIgnoreCase(cookieSameSite)) {
            isSecure = true; // Modern browsers require Secure when SameSite=None
        }

        ResponseCookie cookie = ResponseCookie.from(cookieName, value)
            .httpOnly(true)
            .secure(isSecure)
            .path("/")
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .sameSite(cookieSameSite)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
