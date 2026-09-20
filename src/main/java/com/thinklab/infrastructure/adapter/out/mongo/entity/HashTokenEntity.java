package com.thinklab.infrastructure.adapter.out.mongo.entity;

import com.thinklab.domain.model.HashToken;
import com.thinklab.domain.valueobject.HashAlgorithm;
import com.thinklab.domain.valueobject.HashStatus;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.*;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Infrastructure Entity: Persistence model for the HashToken aggregate mapped to MongoDB.
 *
 * <p><b>Architectural Role:</b>
 * This record serves as the MongoDB persistence mapping for the {@link HashToken} aggregate root.
 * It acts as an Anti-Corruption Layer (ACL), completely decoupling the immutable domain model from
 * database-specific schema structures, BSON serialization types, and Micronaut Data annotations.
 *
 * <p><b>Contractual Obligations:</b>
 * <ul>
 * <li><b>Identity Sovereignty (BSON Binary):</b> Utilizes {@link UUID} directly for primary keys,
 *     guaranteeing native BSON Binary (Subtype 4) storage optimization and high-performance indexing in MongoDB.</li>
 * <li><b>Optimistic Concurrency Control:</b> Implements the Micronaut Data {@link Version} annotation
 *     to prevent data race conditions and silent write overrides in multi-threaded reactive pipelines.</li>
 * <li><b>Multi-Dimensional Indexing:</b> Declares compound indexes on tenant boundaries combined with
 *     status, payloads, and generated hashes to ensure sub-millisecond retrieval speeds.</li>
 * </ul>
 *
 * @param id            The globally unique primary identifier (stored as BSON Binary UUID).
 * @param tenantId      The isolated tenant boundary identifier.
 * @param sourceService The originating microservice or upstream system identifier.
 * @param payload       The raw content prior to hashing.
 * @param generatedHash The computed cryptographic string result.
 * @param algorithm     The cryptographic algorithm strategy utilized.
 * @param status        The current operational lifecycle state of the token.
 * @param createdBy     The verified agent or system that provisioned the token.
 * @param createdAt     The exact UTC instant of initial record materialization.
 * @param updatedBy     The verified agent who last mutated the state (null if never updated).
 * @param updatedAt     The exact UTC instant of the last mutation (null if never updated).
 * @param version       Optimistic locking version counter for database concurrency management.
 *
 * @author ThinkLab
 * @since 1.0
 */
@Serdeable
@Introspected
@MappedEntity("hash_token")
@Indexes({
        @Index(columns = {"tenantId", "status"}),
        @Index(columns = {"tenantId", "payload"}),
        @Index(columns = {"tenantId", "generatedHash"})
})
public record HashTokenEntity(

        @Id
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

        @Version
        Long version
) {

    /**
     * Compact constructor enforcing absolute persistence invariants and structural integrity.
     *
     * <p><b>Contract:</b> Guarantees that no uninitialized, null, or logically blank state can be
     * written to or read from the persistence layer.
     *
     * @throws NullPointerException if any mandatory parameter is null.
     * @throws IllegalArgumentException if any mandatory string parameter is blank.
     */
    public HashTokenEntity {
        Objects.requireNonNull(id, "Persistence Invariant Violation: Token Entity ID cannot be null.");
        Objects.requireNonNull(tenantId, "Persistence Invariant Violation: Tenant ID cannot be null.");
        Objects.requireNonNull(sourceService, "Persistence Invariant Violation: Source Service cannot be null.");
        Objects.requireNonNull(payload, "Persistence Invariant Violation: Payload cannot be null.");
        Objects.requireNonNull(originalPayload, "Persistence Invariant Violation: Original payload cannot be null.");
        Objects.requireNonNull(generatedHash, "Persistence Invariant Violation: Generated Hash cannot be null.");
        Objects.requireNonNull(algorithm, "Persistence Invariant Violation: Algorithm cannot be null.");
        Objects.requireNonNull(status, "Persistence Invariant Violation: Status cannot be null.");
        Objects.requireNonNull(createdBy, "Persistence Invariant Violation: CreatedBy cannot be null.");
        Objects.requireNonNull(createdAt, "Persistence Invariant Violation: CreatedAt timestamp cannot be null.");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Tenant ID cannot be blank.");
        }
        if (sourceService.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Source Service cannot be blank.");
        }
        if (payload.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Payload cannot be blank.");
        }
        if (generatedHash.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Generated Hash cannot be blank.");
        }
        if (createdBy.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: CreatedBy cannot be blank.");
        }
    }

    /**
     * Factory method to map a pure Domain Aggregate Root to this Persistence Entity.
     *
     * @param domain The immutable {@link HashToken} domain aggregate instance.
     * @return A mapped, strictly validated {@link HashTokenEntity} instance ready for MongoDB persistence.
     * @throws NullPointerException if the provided domain aggregate is null.
     */
    public static HashTokenEntity fromDomain(HashToken domain) {
        Objects.requireNonNull(domain, "Infrastructure constraint violated: Domain aggregate cannot be null for entity mapping.");

        return new HashTokenEntity(
                domain.id(),
                domain.tenantId(),
                domain.sourceService(),
                domain.payload(),
                domain.originalPayload(),
                domain.generatedHash(),
                domain.algorithm(),
                domain.status(),
                domain.createdBy(),
                domain.createdAt(),
                domain.updatedBy(),
                domain.updatedAt(),
                domain.version()
        );
    }

    /**
     * Maps the persistence entity back to the pure Domain Aggregate Root.
     *
     * @return A pristine, immutable {@link HashToken} domain aggregate instance.
     */
    public HashToken toDomain() {
        return new HashToken(
                id,
                tenantId,
                sourceService,
                payload,
                originalPayload,
                generatedHash,
                algorithm,
                status,
                createdBy,
                createdAt,
                updatedBy,
                updatedAt,
                version != null ? version : 0L
        );
    }
}