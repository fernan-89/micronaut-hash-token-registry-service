package com.thinklab.application.port.out;

import com.thinklab.domain.model.HashToken;
import com.thinklab.domain.valueobject.HashStatus;
import io.micronaut.data.model.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application Port: Output boundary for the {@link HashToken} aggregate persistence layer.
 *
 * <p><b>Architectural Role:</b>
 * This interface defines the outbound persistence contract (driven port) that infrastructure adapters
 * must implement to guarantee atomic, consistent storage and retrieval operations for the cryptographic
 * hash registry. Designed for mission-critical systems, it strictly enforces reactive, non-blocking
 * communication patterns between the core domain layer and underlying persistence adapters.
 *
 * <p><b>Contractual Obligations:</b>
 * <ul>
 * <li><b>Reactive Non-Blocking Stream:</b> Operates strictly within the Project Reactor pipeline using
 *     {@link Mono} and {@link Flux}, safeguarding Netty EventLoops from disk I/O blocking.</li>
 * <li><b>Identity Sovereignty:</b> Enforces native {@link UUID} parameters for primary entity lookups
 *     to ensure optimal BSON Binary (Subtype 4) index performance.</li>
 * <li><b>Multi-Tenant Isolation:</b> Mandates tenant-scoped operations across all query, validation,
 *     and listing boundaries to enforce security and compliance.</li>
 * <li><b>Framework Independence:</b> Completely decoupled from infrastructure frameworks, relying solely on
 *     native Java types, Micronaut Data pagination models, and standard reactive primitives.</li>
 * </ul>
 *
 * @author ThinkLab
 * @version 1.0.0
 * @since 1.0
 */
public interface HashTokenRepositoryPort {

    /**
     * Persists the initial state of a newly created cryptographic hash registry aggregate.
     *
     * @param hashToken The aggregate root to be initialized and persisted. Must not be null.
     * @return A {@link Mono} emitting the successfully persisted {@link HashToken} aggregate.
     * @throws NullPointerException if the provided hashToken is null.
     */
    Mono<HashToken> save(HashToken hashToken);

    /**
     * Commits a state mutation (e.g., transition to INACTIVE, ACTIVE, or REVOKED) of an existing
     * aggregate root to the underlying persistent storage.
     *
     * @param hashToken The mutated aggregate root. Must not be null.
     * @return A {@link Mono} emitting the successfully updated {@link HashToken} aggregate.
     * @throws NullPointerException if the provided hashToken is null.
     */
    Mono<HashToken> update(HashToken hashToken);

    /**
     * Retrieves a specific hash registry aggregate by its BSON-compliant UUID primary identifier.
     *
     * @param id The universally unique identifier (UUID) of the hash. Must not be null.
     * @return A {@link Mono} emitting the found {@link HashToken}, or an empty signal if absent.
     * @throws NullPointerException if the provided ID is null.
     */
    Mono<HashToken> findById(UUID id);

    /**
     * Enforces data integrity by verifying the existence of an ACTIVE hash record
     * for a specific tenant boundary and payload context. Used for pre-emptive duplicate prevention.
     *
     * @param tenantId The isolated tenant boundary identifier. Must not be null or blank.
     * @param payload  The original raw payload string to validate. Must not be null.
     * @return A {@link Mono} emitting {@code true} if an active conflict exists; {@code false} otherwise.
     * @throws NullPointerException if any parameter is null.
     */
    Mono<Boolean> existsActiveByTenantAndPayload(String tenantId, String payload);

    /**
     * Retrieves a paginated reactive stream of hash token aggregates scoped to a specific tenant boundary.
     *
     * @param tenantId The isolated tenant boundary identifier. Must not be null or blank.
     * @param pageable Pagination and sorting metadata configuration for large dataset handling. Must not be null.
     * @return A {@link Flux} emitting matching {@link HashToken} aggregates.
     * @throws NullPointerException if any parameter is null.
     */
    Flux<HashToken> findAllByTenantId(String tenantId, Pageable pageable);

    /**
     * Retrieves a paginated reactive stream of hash token aggregates filtered strictly by tenant
     * and specific operational lifecycle status.
     *
     * @param tenantId The isolated tenant boundary identifier. Must not be null or blank.
     * @param status   The specific operational {@link HashStatus} filter (e.g., ACTIVE, INACTIVE, REVOKED). Must not be null.
     * @param pageable Pagination and sorting metadata configuration. Must not be null.
     * @return A {@link Flux} emitting matching {@link HashToken} aggregates.
     * @throws NullPointerException if any parameter is null.
     */
    Flux<HashToken> findAllByTenantIdAndStatus(String tenantId, HashStatus status, Pageable pageable);

    /**
     * Retrieves a paginated reactive stream of hash token aggregates filtered by tenant and the originating
     * source service, regardless of operational status. Backs the Convention B search behavior qualifier.
     *
     * @param tenantId      The isolated tenant boundary identifier. Must not be null or blank.
     * @param sourceService The originating microservice or upstream system identifier. Must not be null or blank.
     * @param pageable      Pagination and sorting metadata configuration. Must not be null.
     * @return A {@link Flux} emitting matching {@link HashToken} aggregates.
     */
    Flux<HashToken> findAllByTenantIdAndSourceService(String tenantId, String sourceService, Pageable pageable);

    /**
     * Retrieves a paginated reactive stream of hash token aggregates filtered by tenant, source service,
     * and operational status simultaneously. Backs the Convention B search behavior qualifier.
     *
     * @param tenantId      The isolated tenant boundary identifier. Must not be null or blank.
     * @param sourceService The originating microservice or upstream system identifier. Must not be null or blank.
     * @param status        The specific operational {@link HashStatus} filter. Must not be null.
     * @param pageable      Pagination and sorting metadata configuration. Must not be null.
     * @return A {@link Flux} emitting matching {@link HashToken} aggregates.
     */
    Flux<HashToken> findAllByTenantIdAndSourceServiceAndStatus(String tenantId, String sourceService, HashStatus status, Pageable pageable);
}