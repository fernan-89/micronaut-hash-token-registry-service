package com.thinklab.domain.model;

import com.thinklab.domain.valueobject.HashAlgorithm;
import com.thinklab.domain.valueobject.HashStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain Model: Aggregate Root representing a cryptographic hash or serial key.
 *
 * <p><b>Architectural Role:</b>
 * This record serves as the immutable Aggregate Root of the Hashing context. It encapsulates
 * the core state and behavioral rules of a HashToken. State transitions are strictly delegated to
 * the underlying {@link HashStatus} state machine, guaranteeing that the aggregate remains in a
 * valid, consistent, and compliant state throughout its lifecycle.
 *
 * <p><b>Contractual Obligations:</b>
 * <ul>
 *   <li><b>Domain Purity:</b> Absolute zero infrastructure or framework dependencies.</li>
 *   <li><b>Identity Sovereignty:</b> The primary identifier must be a universally unique identifier (UUID)
 *       derived from a SHA3-512 deterministic seed.</li>
 *   <li><b>Functional Mutation:</b> All state-altering operations are side-effect-free and return
 *       new instances, preserving strict immutability.</li>
 *   <li><b>Invariant Enforcement:</b> The compact constructor guarantees fail-fast validation. It is
 *       impossible to instantiate an invalid aggregate.</li>
 * </ul>
 *
 * <p><b>Concurrency & Thread-Safety:</b>
 * As a Java Record, this class is deeply immutable and inherently thread-safe. It is fully
 * optimized for concurrent, multi-threaded execution within Project Reactor (Flux/Mono)
 * pipelines without requiring synchronization blocks.
 *
 * @param id            Universally unique identifier (stored as BSON Binary).
 * @param tenantId      Identifier for multi-tenant data isolation.
 * @param sourceService The originating system or microservice name.
 * @param payload       The sanitized content (trimmed, special characters stripped) that was actually hashed.
 * @param originalPayload The raw, unsanitized content exactly as received at the API boundary, retained for forensic comparison.
 * @param generatedHash The resulting cryptographic string.
 * @param algorithm     The cryptographic strategy used for hashing.
 * @param status        The current lifecycle state of the token.
 * @param createdBy     The identity of the creator or system process.
 * @param createdAt     The exact timestamp of creation.
 * @param updatedBy     The identity of the last executor to mutate the state (null if never updated).
 * @param updatedAt     The exact timestamp of the last mutation (null if never updated).
 * @param version       Optimistic locking version for concurrency control in the persistence layer.
 *
 * @author ThinkLab
 * @since 1.0
 */
public record HashToken(
        UUID id,
        String tenantId,
        String sourceService,
        String payload,
        String originalPayload,
        String generatedHash,
        HashAlgorithm algorithm,
        HashStatus status,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        Long version
) {

    /**
     * Compact constructor to enforce absolute domain invariants.
     *
     * <p><b>Contract:</b> Validates that the aggregate never enters an inconsistent state.
     * Defensively rejects nulls and blank strings for mandatory fields.
     *
     * @throws NullPointerException if any mandatory field is null.
     * @throws IllegalArgumentException if any mandatory string field is blank.
     */
    public HashToken {
        Objects.requireNonNull(id, "Domain Invariant Violation: ID cannot be null.");
        Objects.requireNonNull(tenantId, "Domain Invariant Violation: Tenant ID cannot be null.");
        Objects.requireNonNull(sourceService, "Domain Invariant Violation: Source service cannot be null.");
        Objects.requireNonNull(payload, "Domain Invariant Violation: Payload cannot be null.");
        Objects.requireNonNull(originalPayload, "Domain Invariant Violation: Original payload cannot be null.");
        Objects.requireNonNull(generatedHash, "Domain Invariant Violation: Generated hash cannot be null.");
        Objects.requireNonNull(algorithm, "Domain Invariant Violation: Algorithm cannot be null.");
        Objects.requireNonNull(status, "Domain Invariant Violation: Status cannot be null.");
        Objects.requireNonNull(createdBy, "Domain Invariant Violation: Creator identification is mandatory.");
        Objects.requireNonNull(createdAt, "Domain Invariant Violation: Creation timestamp is mandatory.");
        Objects.requireNonNull(version, "Domain Invariant Violation: Version field is required for concurrency control.");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("Domain Invariant Violation: Tenant ID cannot be blank.");
        }
        if (sourceService.isBlank()) {
            throw new IllegalArgumentException("Domain Invariant Violation: Source service cannot be blank.");
        }
        if (generatedHash.isBlank()) {
            throw new IllegalArgumentException("Domain Invariant Violation: Generated hash cannot be blank.");
        }
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("Domain Invariant Violation: Creator identification cannot be blank.");
        }
    }

    /**
     * Factory method for provisioning a newly minted HashToken instance.
     *
     * @param id            The deterministically generated UUID.
     * @param tenantId      Identifier for multi-tenant isolation.
     * @param sourceService Originating system name.
     * @param payload       Sanitized content that will actually be hashed.
     * @param originalPayload Raw content exactly as received, before sanitization.
     * @param generatedHash Resulting cryptographic string.
     * @param algorithm     Strategy used for hashing.
     * @param creator       Identification of the agent or system executing the creation.
     * @return A pristine {@link HashToken} instance in the {@link HashStatus#ACTIVE} state.
     */
    public static HashToken create(
            UUID id,
            String tenantId,
            String sourceService,
            String payload,
            String originalPayload,
            String generatedHash,
            HashAlgorithm algorithm,
            String creator
    ) {
        return new HashToken(
                id,
                tenantId,
                sourceService,
                payload,
                originalPayload,
                generatedHash,
                algorithm,
                HashStatus.ACTIVE,
                creator,
                Instant.now(),
                null,
                null,
                0L
        );
    }

    /**
     * Transitions the token to the INACTIVE state.
     *
     * <p>Executes a fail-fast validation against the {@link HashStatus} state machine.
     *
     * @param executor Identification of the agent performing the deactivation.
     * @return A new immutable instance reflecting the updated status and audit fields.
     * @throws IllegalArgumentException if the executor is null or blank.
     * @throws com.thinklab.domain.exception.InvalidHashStatusException if the state transition is illegal.
     */
    public HashToken deactivate(String executor) {
        Objects.requireNonNull(executor, "Executor identification cannot be null.");
        if (executor.isBlank()) {
            throw new IllegalArgumentException("Executor identification cannot be blank.");
        }

        this.status.validateTransitionTo(HashStatus.INACTIVE);

        return new HashToken(
                id, tenantId, sourceService, payload, originalPayload, generatedHash,
                algorithm, HashStatus.INACTIVE, createdBy, createdAt,
                executor, Instant.now(), version
        );
    }

    /**
     * Transitions the token back to the ACTIVE state.
     *
     * <p>Executes a fail-fast validation against the {@link HashStatus} state machine.
     *
     * @param executor Identification of the agent performing the reactivation.
     * @return A new immutable instance reflecting the updated status and audit fields.
     * @throws IllegalArgumentException if the executor is null or blank.
     * @throws com.thinklab.domain.exception.InvalidHashStatusException if the state transition is illegal.
     */
    public HashToken reactivate(String executor) {
        Objects.requireNonNull(executor, "Executor identification cannot be null.");
        if (executor.isBlank()) {
            throw new IllegalArgumentException("Executor identification cannot be blank.");
        }

        this.status.validateTransitionTo(HashStatus.ACTIVE);

        return new HashToken(
                id, tenantId, sourceService, payload, originalPayload, generatedHash,
                algorithm, HashStatus.ACTIVE, createdBy, createdAt,
                executor, Instant.now(), version
        );
    }

    /**
     * Transitions the token to the terminal REVOKED state.
     *
     * <p>Executes a fail-fast validation against the {@link HashStatus} state machine.
     * Once revoked, the token cannot undergo any further state transitions.
     *
     * @param executor Identification of the agent performing the revocation.
     * @return A new immutable instance reflecting the terminal status and audit fields.
     * @throws IllegalArgumentException if the executor is null or blank.
     * @throws com.thinklab.domain.exception.InvalidHashStatusException if the state transition is illegal.
     */
    public HashToken revoke(String executor) {
        Objects.requireNonNull(executor, "Executor identification cannot be null.");
        if (executor.isBlank()) {
            throw new IllegalArgumentException("Executor identification cannot be blank.");
        }

        this.status.validateTransitionTo(HashStatus.REVOKED);

        return new HashToken(
                id, tenantId, sourceService, payload, originalPayload, generatedHash,
                algorithm, HashStatus.REVOKED, createdBy, createdAt,
                executor, Instant.now(), version
        );
    }
}