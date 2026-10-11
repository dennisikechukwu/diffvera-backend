package me.dennislab.diffvera.installation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for GitHub App installations.
 */
public interface InstallationRepository extends JpaRepository<Installation, UUID> {

    // Find installation by GitHub's installation ID
    Optional<Installation> findByGithubInstallationId(Long githubInstallationId);

    // Find installation by target account handle (e.g. "northstar")
    Optional<Installation> findByAccountLogin(String accountLogin);

    Optional<Installation> findByAccountLoginIgnoreCase(String accountLogin);

    long countByAccountLoginIgnoreCase(String accountLogin);
}
