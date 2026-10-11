package me.dennislab.diffvera.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Service to resolve the currently active developer user.
 * Reads from Spring Security's SecurityContext. If unauthenticated in development,
 * resolves or provisions a fallback local development user.
 */
@Service
@Slf4j
public class CurrentUserService {

    private final UserRepository userRepository;
    private final boolean requireAuth;

    @org.springframework.beans.factory.annotation.Autowired
    public CurrentUserService(
        UserRepository userRepository,
        @Value("${diffvera.security.require-auth:false}") boolean requireAuth
    ) {
        this.userRepository = userRepository;
        this.requireAuth = requireAuth;
    }

    public CurrentUserService(UserRepository userRepository) {
        this(userRepository, false);
    }

    /**
     * Resolves the current authenticated user, or returns an Optional.empty() if unauthenticated.
     */
    public Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * Resolves current user or provisions/retrieves a local developer user for development mode.
     */
    @Transactional
    public User getCurrentUserOrDevFallback() {
        return getCurrentUser().orElseGet(() -> {
            if (requireAuth) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
            }
            return userRepository.findByLogin("dev-user").orElseGet(() -> {
                log.info("Provisioning local development fallback user 'dev-user'");
                User devUser = User.builder()
                    .githubUserId(999999L)
                    .login("dev-user")
                    .name("Diffvera Developer")
                    .email("dev@diffvera.local")
                    .avatarUrl("https://avatars.githubusercontent.com/u/999999?v=4")
                    .build();
                return userRepository.save(devUser);
            });
        });
    }
}
