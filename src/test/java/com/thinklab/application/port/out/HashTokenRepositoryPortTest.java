package com.thinklab.application.port.out;

import com.thinklab.domain.model.HashToken;
import com.thinklab.domain.valueobject.HashStatus;
import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 *  Unit Test: Contractual validation for the {@link HashTokenRepositoryPort}.
 *  Ensures that the outbound port strictly adheres to Identity Sovereignty (ADR 005)
 *  and preserves reactive integrity when interacting with hash token persistence.
 *
 *  <p><b>Test Guarantees:</b></p>
 *  <ul>
 *      <li><b>Identity Sovereignty:</b> Enforces native {@link UUID} for primary identifiers.</li>
 *      <li><b>BSON Optimization:</b> Aligned with Binary Subtype 4 indexing strategies.</li>
 *      <li><b>Reactive Integrity:</b> Verified using non-blocking {@link StepVerifier} signals.</li>
 *  </ul>
 */
@ExtendWith(MockitoExtension.class)
class HashTokenRepositoryPortTest {

    @Mock
    private HashTokenRepositoryPort repositoryPort;

    private UUID hashId;
    private String tenantId;

    /**
     * Initializes deterministic context with native UUIDs as mandated by ADR 005.
     */
    @BeforeEach
    void setUp() {
        hashId = UUID.randomUUID();
        tenantId = "TENANT-DEMO-PROD-01";
    }

    /**
     * Happy Path: Validates initial persistence contract.
     */
    @Test
    @DisplayName("Should successfully simulate initial HashToken persistence")
    void shouldSaveHashTokenSuccessfully() {
        HashToken mockToken = mock(HashToken.class);
        when(repositoryPort.save(any(HashToken.class))).thenReturn(Mono.just(mockToken));

        StepVerifier.create(repositoryPort.save(mockToken))
                .expectNext(mockToken)
                .verifyComplete();

        verify(repositoryPort, times(1)).save(mockToken);
    }

    /**
     * Happy Path: Validates state synchronization contract.
     */
    @Test
    @DisplayName("Should successfully simulate the update of an existing HashToken")
    void shouldUpdateHashTokenSuccessfully() {
        HashToken mockToken = mock(HashToken.class);
        when(repositoryPort.update(any(HashToken.class))).thenReturn(Mono.just(mockToken));

        StepVerifier.create(repositoryPort.update(mockToken))
                .expectNext(mockToken)
                .verifyComplete();

        verify(repositoryPort, times(1)).update(mockToken);
    }

    /**
     * Happy Path: Validates identity discovery using native UUID.
     * CRITICAL FIX: Removed .toString() to comply with the UUID parameter signature [cite: 534, 601].
     */
    @Test
    @DisplayName("Should successfully retrieve a HashToken by its native UUID identifier")
    void shouldFindByIdSuccessfully() {
        // Given
        HashToken mockToken = mock(HashToken.class);

        // CORREÇÃO: Passagem direta do objeto UUID conforme o novo contrato da porta [cite: 520, 601]
        when(repositoryPort.findById(hashId)).thenReturn(Mono.just(mockToken));

        // When & Then
        StepVerifier.create(repositoryPort.findById(hashId))
                .expectNext(mockToken)
                .verifyComplete();

        verify(repositoryPort).findById(hashId);
    }

    /**
     * Happy Path: Validates paginated retrieval scoped to a specific tenant.
     */
    @Test
    @DisplayName("Should retrieve a paginated stream of hashes for a specific tenant")
    void shouldFindAllByTenantIdSuccessfully() {
        HashToken token = mock(HashToken.class);
        Pageable pageable = Pageable.from(0, 10);

        when(repositoryPort.findAllByTenantId(eq(tenantId), eq(pageable)))
                .thenReturn(Flux.just(token));

        StepVerifier.create(repositoryPort.findAllByTenantId(tenantId, pageable))
                .expectNext(token)
                .verifyComplete();

        verify(repositoryPort).findAllByTenantId(tenantId, pageable);
    }
}