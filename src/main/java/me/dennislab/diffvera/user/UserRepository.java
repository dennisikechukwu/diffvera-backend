package me.dennislab.diffvera.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for developer accounts.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    // Look up an existing user by their GitHub numeric ID
    Optional<User> findByGithubUserId(Long githubUserId);

    // Look up an existing user by their GitHub username
    Optional<User> findByLogin(String login);
}
