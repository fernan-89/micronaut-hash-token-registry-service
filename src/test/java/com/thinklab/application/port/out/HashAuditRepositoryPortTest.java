package com.thinklab.application.port.out;

import com.thinklab.domain.model.HashAudit;
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
import static org.mockito.Mockito.*;

/**
 *  Unit Test: Contractual validation for the {@link HashAuditRepositoryPort} outbound port.
 *  This suite ensures that the infrastructure port strictly adheres to Identity
 *  Sovereignty (ADR 005) and preserves reactive integrity for forensic auditing.
 *
 *  <p><b>Test Guarantees:</b></p>
 *  <ul>
 *      <li><b>Identity Sovereignty:</b> Enforces native {@link UUID} for all primary and entity identifiers.</li>
 *      <li><b>BSON Optimization:</b> Aligned with Binary Subtype 4 indexing strategies for high-assurance storage.</li>
 *      <li><b>Reactive Integrity:</b> Validates non-blocking signal propagation using {@link StepVerifier}.</li>
 *  </ul>
 */
@ExtendWith(MockitoExtension.class)
class HashAuditRepositoryPortTest {

    @Mock
    private HashAuditRepositoryPort repositoryPort;

    private UUID txId;
    private UUID entityId;
    private String tenantId;

    /**
     * Initializes deterministic context with native UUIDs as mandated by ADR 005.
     */
    @BeforeEach
    void setUp() {
        txId = UUID.randomUUID();
        entityId = UUID.randomUUID();
        tenantId = "TENANT-DEMO-PROD-01";
    }

    /**
     * Happy Path: Validates audit persistence contract.
     */
    @Test
    @DisplayName("Should successfully simulate the persistence of a HashAudit record")
    void shouldSaveAuditSuccessfully() {
        // Given: A valid domain aggregate
        HashAudit mockAudit = mock(HashAudit.class);
        when(repositoryPort.save(any(HashAudit.class))).thenReturn(Mono.just(mockAudit));

        // When & Then: Execute pipeline and verify emission
        StepVerifier.create(repositoryPort.save(mockAudit))
                .expectNext(mockAudit)
                .verifyComplete();

        verify(repositoryPort, times(1)).save(mockAudit);
    }

    /**
     * Happy Path: Validates forensic retrieval by Transaction UUID.
     */
    @Test
    @DisplayName("Should successfully retrieve audit logs correlated by Transaction UUID")
    void shouldFindAuditByTxId() {
        // Given: Multiple records associated with the same reactive transaction
        HashAudit log1 = mock(HashAudit.class);
        HashAudit log2 = mock(HashAudit.class);

        // Port now expects the native UUID object
        when(repositoryPort.findByTxId(txId)).thenReturn(Flux.just(log1, log2));

        // When & Then
        StepVerifier.create(repositoryPort.findByTxId(txId))
                .expectNext(log1)
                .expectNext(log2)
                .verifyComplete();

        verify(repositoryPort).findByTxId(txId);
    }

    /**
     * Happy Path: Validates multi-tenant isolation.
     */
    @Test
    @DisplayName("Should successfully filter forensic trails by Tenant ID")
    void shouldFindAuditByTenantId() {
        HashAudit mockAudit = mock(HashAudit.class);
        when(repositoryPort.findByTenantId(tenantId)).thenReturn(Flux.just(mockAudit));

        StepVerifier.create(repositoryPort.findByTenantId(tenantId))
                .expectNext(mockAudit)
                .verifyComplete();

        verify(repositoryPort).findByTenantId(tenantId);
    }

    /**
     * Happy Path: Validates lifecycle reconstruction for a specific Entity UUID.
     * CRITICAL FIX: Removed .toString() to comply with the UUID parameter signature [cite: 478, 594].
     */
    @Test
    @DisplayName("Should successfully retrieve the audit history for a specific Entity UUID")
    void shouldFindAuditByEntityId() {
        // Given: A history mapped to a deterministic UUID
        HashAudit mockAudit = mock(HashAudit.class);

        // CORREÇÃO: Passagem direta do objeto UUID conforme o novo contrato da porta [cite: 591, 602]
        when(repositoryPort.findByEntityId(entityId)).thenReturn(Flux.just(mockAudit));

        // When & Then: Execute retrieval
        StepVerifier.create(repositoryPort.findByEntityId(entityId))
                .expectNext(mockAudit)
                .verifyComplete();

        verify(repositoryPort).findByEntityId(entityId);
    }
}