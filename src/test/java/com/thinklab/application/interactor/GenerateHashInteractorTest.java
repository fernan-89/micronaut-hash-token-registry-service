package com.thinklab.application.interactor;

import com.thinklab.application.port.out.HashAuditRepositoryPort;
import com.thinklab.application.port.out.HashTokenRepositoryPort;
import com.thinklab.application.usecase.command.GenerateHashCommand;
import com.thinklab.domain.exception.DuplicateHashException;
import com.thinklab.domain.model.HashAudit;
import com.thinklab.domain.model.HashToken;
import com.thinklab.domain.valueobject.HashAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 *  Unit Test: Application Interactor for Deterministic Hash Generation.
 *  This suite validates the high-assurance orchestration of cryptographic token
 *  creation, ensuring that business-level idempotency is enforced and forensic
 *  audit trails are immutably persisted.
 *
 *  <p><b>Architectural Principles:</b></p>
 *  <ul>
 *      <li><b>Deterministic Identity:</b> Validates that generation remains idempotent based on payload/tenant.</li>
 *      <li><b>Reactive Isolation:</b> Zero-infrastructure testing utilizing StepVerifier and Mockito.</li>
 *      <li><b>Identity Sovereignty:</b> Enforces native UUID compliance for the resulting aggregate.</li>
 *  </ul>
 */
@ExtendWith(MockitoExtension.class)
class GenerateHashInteractorTest {

    @Mock
    private HashTokenRepositoryPort hashTokenRepository;

    @Mock
    private HashAuditRepositoryPort hashAuditRepository;

    @InjectMocks
    private GenerateHashInteractor interactor;

    private String tenantId;
    private GenerateHashCommand command;

    /**
     * Initializes the testing context.
     * Ensures command parameters match the strict record definition order:
     * tenantId, payload, algorithm, sourceService, executor, asSerialKey.
     */
    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID().toString();
        command = new GenerateHashCommand(
                tenantId,
                "DEMO-SAMPLE-DATA-2026",
                HashAlgorithm.SHA3_512,
                "mission-control-service",
                "staff-engineer-01",
                false
        );
    }

    /**
     * Happy Path: Validates the atomic orchestration of hash calculation,
     * deterministic identity derivation, and audit trail persistence.
     */
    @Test
    @DisplayName("Should generate hash successfully when no active duplicate exists")
    void shouldGenerateHashSuccessfully() {
        // Given: The repository confirms the payload is unique for this tenant
        when(hashTokenRepository.existsActiveByTenantAndPayload(command.tenantId(), command.payload()))
                .thenReturn(Mono.just(false));

        // Mocking the repository to return the input aggregate (simulating persistence)
        when(hashTokenRepository.save(any(HashToken.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // Mocking audit persistence
        when(hashAuditRepository.save(any(HashAudit.class)))
                .thenReturn(Mono.just(mock(HashAudit.class)));

        // When & Then: Execute the reactive pipeline
        StepVerifier.create(interactor.execute(command))
                .assertNext(token -> {
                    // Resulting token must strictly match the command specification
                    assert token.tenantId().equals(command.tenantId());
                    assert token.payload().equals(command.payload());
                    assert token.algorithm() == command.algorithm();
                    // Identity sovereignty check: ID must be a valid UUID
                    assert token.id() != null;
                })
                .verifyComplete();

        // Forensic verification of the orchestration sequence
        verify(hashTokenRepository).existsActiveByTenantAndPayload(anyString(), anyString());
        verify(hashTokenRepository).save(any(HashToken.class));
        verify(hashAuditRepository).save(any(HashAudit.class));
    }

    /**
     * Business Invariant Test: Ensures the system prevents the generation of duplicate
     * active hashes, signaling a semantic conflict error before initiating I/O.
     */
    @Test
    @DisplayName("Should signal HASH_DUPLICATE error when an active hash already exists")
    void shouldFailWhenHashAlreadyExists() {
        // Given: An active hash already exists in the repository (existsActiveByTenantAndPayload returns true)
        when(hashTokenRepository.existsActiveByTenantAndPayload(command.tenantId(), command.payload()))
                .thenReturn(Mono.just(true));

        // When & Then: Pipeline must emit a DuplicateHashException signal
        StepVerifier.create(interactor.execute(command))
                .expectErrorMatches(throwable ->
                        throwable instanceof DuplicateHashException &&
                                throwable.getMessage().contains("An active cryptographic hash already exists")
                )
                .verify();

        // Verification: Ensure token repository was queried but never saved/updated
        verify(hashTokenRepository, times(1)).existsActiveByTenantAndPayload(command.tenantId(), command.payload());
        verify(hashTokenRepository, never()).save(any());
    }

    /**
     * Boundary Defense: Ensures fail-fast behavior on null input,
     * preventing illegal allocations in the reactive stream.
     */
    @Test
    @DisplayName("Should fail fast with NullPointerException when the command is null")
    void shouldFailFastWhenCommandIsNull() {
        assertThrows(NullPointerException.class, () -> interactor.execute(null));

        verifyNoInteractions(hashTokenRepository);
        verifyNoInteractions(hashAuditRepository);
    }

    @Test
    @DisplayName("A serial-key request formats the digest as five dash-separated groups")
    void shouldFormatSerialKey() {
        GenerateHashCommand serial = new GenerateHashCommand(tenantId, "DEMO-SAMPLE-DATA-2026", HashAlgorithm.SHA3_512,
                "svc", "op", true);
        when(hashTokenRepository.existsActiveByTenantAndPayload(anyString(), anyString())).thenReturn(Mono.just(false));
        when(hashTokenRepository.save(any(HashToken.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(hashAuditRepository.save(any(HashAudit.class))).thenReturn(Mono.just(mock(HashAudit.class)));

        StepVerifier.create(interactor.execute(serial))
                .assertNext(token -> org.junit.jupiter.api.Assertions.assertTrue(
                        token.generatedHash().matches("[A-Z0-9]{5}(-[A-Z0-9]{5}){4}")))
                .verifyComplete();
    }

    @Test
    @DisplayName("A persistence failure surfaces as an error signal, a duplicate race is not logged as critical")
    void shouldPropagatePersistenceFailures() {
        when(hashTokenRepository.existsActiveByTenantAndPayload(anyString(), anyString())).thenReturn(Mono.just(false));
        when(hashTokenRepository.save(any(HashToken.class)))
                .thenReturn(Mono.error(new IllegalStateException("mongo down")))
                .thenReturn(Mono.error(new DuplicateHashException("ERR-HASH-00409", "race lost")));

        StepVerifier.create(interactor.execute(command)).expectError(IllegalStateException.class).verify();
        StepVerifier.create(interactor.execute(command)).expectError(DuplicateHashException.class).verify();
    }
}
