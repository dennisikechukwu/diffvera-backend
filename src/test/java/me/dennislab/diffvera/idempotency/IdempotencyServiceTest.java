package me.dennislab.diffvera.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository);
    }

    @Test
    @DisplayName("Should successfully acquire lock on first delivery")
    void shouldAcquireNewKey() {
        String key = "delivery-uuid-1234";
        when(repository.existsById(key)).thenReturn(false);

        boolean acquired = service.tryAcquire(key);

        assertTrue(acquired);
        verify(repository).save(any(IdempotencyKey.class));
    }

    @Test
    @DisplayName("Should reject and drop duplicate delivery key")
    void shouldRejectDuplicateKey() {
        String key = "delivery-uuid-1234";
        when(repository.existsById(key)).thenReturn(true);

        boolean acquired = service.tryAcquire(key);

        assertFalse(acquired);
        verify(repository, never()).save(any(IdempotencyKey.class));
    }

    @Test
    @DisplayName("Should safely handle null or blank key")
    void shouldHandleBlankKey() {
        assertTrue(service.tryAcquire(null));
        assertTrue(service.tryAcquire("  "));
        assertFalse(service.exists(null));
    }
}
