package me.dennislab.diffvera.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.dennislab.diffvera.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Security filter that inspects incoming requests for the signed
 * HTTP-only JWT cookie ('diffvera_session') and populates the SecurityContext.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthService authService;
    private final String cookieName;

    public JwtAuthenticationFilter(
        JwtService jwtService,
        AuthService authService,
        @Value("${diffvera.jwt.cookie-name:diffvera_session}") String cookieName
    ) {
        this.jwtService = jwtService;
        this.authService = authService;
        this.cookieName = cookieName;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        extractTokenFromCookie(request)
            .flatMap(jwtService::extractUserId)
            .flatMap(authService::findUserById)
            .ifPresent(this::setSecurityContext);

        filterChain.doFilter(request, response);
    }

    /**
     * Finds the session cookie by name from the HTTP request.
     */
    private Optional<String> extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies())
            .filter(c -> cookieName.equals(c.getName()))
            .map(Cookie::getValue)
            .filter(val -> val != null && !val.isBlank())
            .findFirst();
    }

    /**
     * Sets authenticated user principal in Spring Security context.
     */
    private void setSecurityContext(User user) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            user,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
