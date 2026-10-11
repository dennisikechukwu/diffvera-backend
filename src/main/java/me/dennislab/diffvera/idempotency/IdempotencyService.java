package me.dennislab.diffvera.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Service managing deduplication keys to ensure webhook deliveries
 * and critical mutations are processed exactly once.
 */
@Service
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    /**
     * Checks if a key has already been processed.
     */
    @Transactional(readOnly = true)
    public boolean exists(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return idempotencyKeyRepository.existsById(key);
    }

    /**
     * Attempts to acquire an idempotency key.
     * Returns true if this is the first time the key is seen (lock acquired).
     * Returns false if the key already exists (duplicate delivery).
     */
    @Transactional
    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }

        if (idempotencyKeyRepository.existsById(key)) {
            log.info("Duplicate idempotency key detected: {}", key);
            return false;
        }

        try {
            IdempotencyKey record = IdempotencyKey.builder()
                .key(key)
                .createdAt(OffsetDateTime.now())
                .build();
            idempotencyKeyRepository.save(record);
            return true;
        } catch (Exception e) {
            log.warn("Concurrent duplicate insertion for idempotency key {}: {}", key, e.getMessage());
            return false;
        }
    }
}
