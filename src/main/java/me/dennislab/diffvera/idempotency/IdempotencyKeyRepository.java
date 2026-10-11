package me.dennislab.diffvera.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for idempotency keys.
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    // Check if key has already been processed
    boolean existsByKey(String key);
}
