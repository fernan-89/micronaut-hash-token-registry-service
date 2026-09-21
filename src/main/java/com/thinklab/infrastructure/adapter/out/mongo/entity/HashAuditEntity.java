package com.thinklab.infrastructure.adapter.out.mongo.entity;

import com.thinklab.domain.model.HashAudit;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Infrastructure Entity: Persistence model for the forensic audit trail mapped to the MongoDB collection.
 *
 * <p><b>Architectural Role:</b>
 * This record serves as the MongoDB persistence mapping for the {@link HashAudit} domain aggregate.
 * It acts as an Anti-Corruption Layer (ACL), completely decoupling the immutable domain model from
 * database-specific schema details, BSON types, and Micronaut Data annotations.
 *
 * <p><b>Contractual Obligations:</b>
 * <ul>
 * <li><b>Append-Only Forensic Integrity:</b> Enforces structural invariants to ensure the audit trail
 *     remains immutable and historically accurate post-persistence.</li>
 * <li><b>Identity Sovereignty (BSON Binary):</b> Utilizes {@link UUID} for primary keys, transaction IDs,
 *     and entity references, ensuring native BSON Binary (Subtype 4) storage optimization in MongoDB. Note that
 *     the primary key is supplied directly by the domain layer to prevent Micronaut Data generation exceptions.</li>
 * <li><b>High-Performance Indexing:</b> Declares multi-dimensional indexes on transaction correlation IDs,
 *     tenant boundaries, entity identifiers, and timestamps for real-time forensic auditing queries.</li>
 * </ul>
 *
 * @param id         The globally unique primary identifier (stored as BSON Binary UUID). Supplied by domain to prevent generation errors.
 * @param txId       The reactive transaction correlation UUID.
 * @param tenantId   The strictly isolated tenant boundary owner.
 * @param entityId   The deterministic UUID of the targeted {@link com.thinklab.domain.model.HashToken}.
 * @param operation  The standardized business operation executed (e.g., "CREATION", "REVOCATION").
 * @param status     The definitive operational outcome (e.g., "SUCCESS").
 * @param executor   The verified agent who authorized the action.
 * @param timestamp  The exact UTC instant the event materialized.
 * @param metadata   Rich contextual telemetry key-value pairs.
 *
 * @author ThinkLab
 * @since 1.0
 */
@Serdeable
@Introspected
@MappedEntity("hash_audit")
@Indexes({
        @Index(columns = {"txId"}),
        @Index(columns = {"tenantId"}),
        @Index(columns = {"entityId"}),
        @Index(columns = {"executor"}),
        @Index(columns = {"timestamp"})
})
public record HashAuditEntity(

        @Id
        UUID id,

        UUID txId,

        String tenantId,

        UUID entityId,

        String operation,

        String status,

        String executor,

        Instant timestamp,

        Map<String, Object> metadata
) {

    /**
     * Compact constructor enforcing absolute persistence invariants and structural integrity.
     *
     * <p><b>Contract:</b> Guarantees that no uninitialized or logically invalid state can be written
     * to the database. Defensively copies nested maps.
     *
     * @throws NullPointerException if any mandatory parameter is null, or if metadata contains null keys/values.
     * @throws IllegalArgumentException if any mandatory string parameter is blank.
     */
    public HashAuditEntity {
        Objects.requireNonNull(id, "Persistence Invariant Violation: Audit Entity ID cannot be null.");
        Objects.requireNonNull(txId, "Persistence Invariant Violation: Transaction ID cannot be null.");
        Objects.requireNonNull(tenantId, "Persistence Invariant Violation: Tenant ID cannot be null.");
        Objects.requireNonNull(entityId, "Persistence Invariant Violation: Entity ID cannot be null.");
        Objects.requireNonNull(operation, "Persistence Invariant Violation: Operation cannot be null.");
        Objects.requireNonNull(status, "Persistence Invariant Violation: Status cannot be null.");
        Objects.requireNonNull(executor, "Persistence Invariant Violation: Executor cannot be null.");
        Objects.requireNonNull(timestamp, "Persistence Invariant Violation: Timestamp cannot be null.");

        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Tenant ID cannot be blank.");
        }
        if (operation.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Operation cannot be blank.");
        }
        if (status.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Status cannot be blank.");
        }
        if (executor.isBlank()) {
            throw new IllegalArgumentException("Persistence Invariant Violation: Executor ID cannot be blank.");
        }

        // Defensively secure the metadata map using Map.copyOf (intrinsically rejects null keys and values).
        metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
    }

    /**
     * Factory method to map a pure Domain Audit model to this Persistence Entity.
     *
     * @param domain The immutable {@link HashAudit} domain aggregate record. Must not be null.
     * @return A mapped, strictly validated {@link HashAuditEntity} instance ready for MongoDB insertion.
     * @throws NullPointerException if the provided domain aggregate is null.
     */
    public static HashAuditEntity fromDomain(HashAudit domain) {
        Objects.requireNonNull(domain, "Infrastructure constraint violated: Domain audit aggregate cannot be null for entity mapping.");

        return new HashAuditEntity(
                domain.id(),
                domain.txId(),
                domain.tenantId(),
                domain.entityId(),
                domain.operation(),
                domain.status(),
                domain.executorId(),
                domain.timestamp(),
                domain.metadata()
        );
    }

    /**
     * Maps the persistence entity back to the pure Domain Audit model.
     *
     * @return A pristine, immutable {@link HashAudit} domain record instance.
     */
    public HashAudit toDomain() {
        return new HashAudit(
                id,
                txId,
                tenantId,
                entityId,
                operation,
                status,
                executor,
                timestamp,
                metadata
        );
    }
}