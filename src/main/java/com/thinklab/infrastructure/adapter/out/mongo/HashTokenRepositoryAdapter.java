package com.thinklab.infrastructure.adapter.out.mongo;

import com.thinklab.application.port.out.HashTokenRepositoryPort;
import com.thinklab.domain.model.HashToken;
import com.thinklab.domain.valueobject.HashStatus;
import com.thinklab.infrastructure.adapter.out.mongo.entity.HashTokenEntity;
import com.thinklab.infrastructure.adapter.out.mongo.repository.HashTokenMongoRepository;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Infrastructure Adapter: Implementation of the {@link HashTokenRepositoryPort} for MongoDB.
 *
 * <p><b>Architectural Role:</b>
 * This adapter serves as the Anti-Corruption Layer (ACL) between the core Domain layer and
 * the MongoDB persistence storage. It encapsulates bidirectional mapping logic to shield the domain
 * from database-specific schemas, ensuring robust lifecycle and consistency management.
 *
 * <p><b>Contractual Obligations:</b>
 * <ul>
 * <li><b>Constructor Injection (ADR-001):</b> Uses an explicit {@link Inject} constructor instead of
 *     Lombok generation to guarantee deterministic bean wiring and AOP proxy reliability.</li>
 * <li><b>Non-Blocking I/O:</b> 100% reactive pipeline using Project Reactor {@link Mono} and {@link Flux},
 *     protecting Netty EventLoops from disk I/O blocking.</li>
 * <li><b>Identity Sovereignty:</b> Enforces native {@link UUID} parameters to maintain strict
 *     BSON Binary (Subtype 4) performance optimization.</li>
 * <li><b>Persistence Resilience (TD-004):</b> Safeguards existence checks with explicit
 *     {@code .defaultIfEmpty(false)} handling to prevent reactive pipeline collapse.</li>
 * </ul>
 *
 * @author ThinkLab
 * @version 1.0.0
 * @since 1.0
 */
@Slf4j
@Singleton
public class HashTokenRepositoryAdapter implements HashTokenRepositoryPort {

    private final HashTokenMongoRepository repository;

    /**
     * Explicit constructor for strict dependency injection (ADR-001).
     *
     * @param repository The underlying MongoDB reactive repository for token persistence. Must not be null.
     * @throws NullPointerException if the repository instance is null.
     */
    @Inject
    public HashTokenRepositoryAdapter(HashTokenMongoRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Infrastructure constraint violated: HashTokenMongoRepository cannot be null.");
    }

    /**
     * Persists a newly created hash aggregate into the MongoDB collection in a non-blocking manner.
     *
     * @param hashToken The pure domain aggregate to be saved. Must not be null.
     * @return A {@link Mono} emitting the successfully persisted domain aggregate.
     * @throws NullPointerException if the provided hash token is null.
     */
    @Override
    public Mono<HashToken> save(HashToken hashToken) {
        Objects.requireNonNull(hashToken, "Infrastructure constraint violated: HashToken aggregate cannot be null for persistence.");

        return Mono.just(hashToken)
                .map(HashTokenEntity::fromDomain)
                .flatMap(repository::save)
                .map(HashTokenEntity::toDomain)
                .doOnSubscribe(s -> log.debug("[ACTION: PERSIST_TOKEN] [ID: {}] [TENANT: {}] - Initiating save operation.", hashToken.id(), hashToken.tenantId()))
                .doOnSuccess(saved -> log.debug("[ACTION: PERSIST_TOKEN] [ID: {}] - Hash token successfully committed to database.", saved.id()))
                .doOnError(e -> log.error("[ACTION: PERSIST_TOKEN] [TENANT: {}] - CRITICAL: Failed to save hash token. Error: {}", hashToken.tenantId(), e.getMessage(), e));
    }

    /**
     * Updates an existing hash aggregate in the MongoDB collection, respecting optimistic locking versioning.
     *
     * @param hashToken The modified domain aggregate to be updated. Must not be null.
     * @return A {@link Mono} emitting the updated domain aggregate.
     * @throws NullPointerException if the provided hash token is null.
     */
    @Override
    public Mono<HashToken> update(HashToken hashToken) {
        Objects.requireNonNull(hashToken, "Infrastructure constraint violated: HashToken aggregate cannot be null for update.");

        return Mono.just(hashToken)
                .map(HashTokenEntity::fromDomain)
                .flatMap(repository::update)
                .map(HashTokenEntity::toDomain)
                .doOnSubscribe(s -> log.debug("[ACTION: UPDATE_TOKEN] [ID: {}] [TENANT: {}] - Initiating update operation.", hashToken.id(), hashToken.tenantId()))
                .doOnSuccess(updated -> log.debug("[ACTION: UPDATE_TOKEN] [ID: {}] - Hash token successfully updated.", updated.id()))
                .doOnError(e -> log.error("[ACTION: UPDATE_TOKEN] [ID: {}] - CRITICAL: Failed to update hash token. Error: {}", hashToken.id(), e.getMessage(), e));
    }

    /**
     * Retrieves a hash token by its BSON-compliant UUID primary identifier.
     *
     * @param id The universally unique identifier (UUID). Must not be null.
     * @return A {@link Mono} emitting the found hash token or an empty signal if not present.
     * @throws NullPointerException if the provided ID is null.
     */
    @Override
    public Mono<HashToken> findById(UUID id) {
        Objects.requireNonNull(id, "Infrastructure constraint violated: Identifier UUID is mandatory for retrieval.");

        return repository.findById(id)
                .map(HashTokenEntity::toDomain)
                .doOnSubscribe(s -> log.trace("[ACTION: FIND_TOKEN_ID] [ID: {}] - Fetching hash registry from database.", id))
                .doOnSuccess(token -> {
                    if (token != null) {
                        log.trace("[ACTION: FIND_TOKEN_ID] [ID: {}] - Hash registry successfully resolved.", id);
                    } else {
                        log.trace("[ACTION: FIND_TOKEN_ID] [ID: {}] - No hash registry found for identifier.", id);
                    }
                })
                .doOnError(e -> log.error("[ACTION: FIND_TOKEN_ID] [ID: {}] - Error retrieving hash registry: {}", id, e.getMessage(), e));
    }

    /**
     * Checks for the existence of an ACTIVE hash for a specific tenant and raw payload.
     *
     * <p><b>Resilience Note (TD-004):</b> Appends {@code .defaultIfEmpty(false)} to prevent
     * reactive pipeline collapse if the MongoDB driver emits an empty cursor signal.
     *
     * @param tenantId The isolated tenant context. Must not be null or blank.
     * @param payload  The original payload string. Must not be null.
     * @return A {@link Mono} emitting {@code true} if an active registry exists; {@code false} otherwise.
     */
    @Override
    public Mono<Boolean> existsActiveByTenantAndPayload(String tenantId, String payload) {
        Objects.requireNonNull(tenantId, "Infrastructure constraint violated: Tenant ID is mandatory.");
        Objects.requireNonNull(payload, "Infrastructure constraint violated: Payload is mandatory.");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("Infrastructure constraint violated: Tenant ID cannot be blank.");
        }

        return repository.existsByTenantIdAndPayloadAndStatus(tenantId, payload, HashStatus.ACTIVE)
                .defaultIfEmpty(false)
                .doOnNext(exists -> log.trace("[ACTION: EXISTS_TOKEN] [TENANT: {}] - Existence check result: {}", tenantId, exists));
    }

    /**
     * Retrieves all hashes for a tenant using reactive pagination.
     *
     * @param tenantId The isolated tenant context. Must not be null or blank.
     * @param pageable The pagination and sorting metadata configuration. Must not be null.
     * @return A {@link Flux} stream of matching domain aggregates.
     */
    @Override
    public Flux<HashToken> findAllByTenantId(String tenantId, Pageable pageable) {
        Objects.requireNonNull(tenantId, "Infrastructure constraint violated: Tenant ID is mandatory.");
        Objects.requireNonNull(pageable, "Infrastructure constraint violated: Pageable configuration cannot be null.");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("Infrastructure constraint violated: Tenant ID cannot be blank.");
        }

        return repository.findByTenantId(tenantId, pageable)
                .map(HashTokenEntity::toDomain)
                .doOnSubscribe(s -> log.trace("[ACTION: LIST_TOKENS] [TENANT: {}] [PAGE: {}] - Fetching paginated tenant hashes.", tenantId, pageable.getNumber()))
                .doOnError(e -> log.error("[ACTION: LIST_TOKENS] [TENANT: {}] - Error listing tenant hashes: {}", tenantId, e.getMessage(), e));
    }

    /**
     * Retrieves filtered hashes for a tenant using reactive pagination and operational status.
     *
     * @param tenantId The isolated tenant context. Must not be null or blank.
     * @param status   The specific operational status filter. Must not be null.
     * @param pageable The pagination and sorting metadata configuration. Must not be null.
     * @return A {@link Flux} stream of matching domain aggregates.
     */
    @Override
    public Flux<HashToken> findAllByTenantIdAndStatus(String tenantId, HashStatus status, Pageable pageable) {
        Objects.requireNonNull(tenantId, "Infrastructure constraint violated: Tenant ID is mandatory.");
        Objects.requireNonNull(status, "Infrastructure constraint violated: HashStatus filter cannot be null.");
        Objects.requireNonNull(pageable, "Infrastructure constraint violated: Pageable configuration cannot be null.");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("Infrastructure constraint violated: Tenant ID cannot be blank.");
        }

        return repository.findByTenantIdAndStatus(tenantId, status, pageable)
                .map(HashTokenEntity::toDomain)
                .doOnSubscribe(s -> log.trace("[ACTION: LIST_TOKENS_STATUS] [TENANT: {}] [STATUS: {}] [PAGE: {}] - Fetching filtered tenant hashes.", tenantId, status, pageable.getNumber()))
                .doOnError(e -> log.error("[ACTION: LIST_TOKENS_STATUS] [TENANT: {}] [STATUS: {}] - Error listing filtered hashes: {}", tenantId, status, e.getMessage(), e));
    }

    /**
     * Retrieves filtered hashes for a tenant and source service using reactive pagination.
     *
     * @param tenantId      The isolated tenant context. Must not be null or blank.
     * @param sourceService The originating microservice or upstream system identifier. Must not be null or blank.
     * @param pageable      The pagination and sorting metadata configuration. Must not be null.
     * @return A {@link Flux} stream of matching domain aggregates.
     */
    @Override
    public Flux<HashToken> findAllByTenantIdAndSourceService(String tenantId, String sourceService, Pageable pageable) {
        Objects.requireNonNull(tenantId, "Infrastructure constraint violated: Tenant ID is mandatory.");
        Objects.requireNonNull(sourceService, "Infrastructure constraint violated: Source service is mandatory.");
        Objects.requireNonNull(pageable, "Infrastructure constraint violated: Pageable configuration cannot be null.");

        return repository.findByTenantIdAndSourceService(tenantId, sourceService, pageable)
                .map(HashTokenEntity::toDomain)
                .doOnSubscribe(s -> log.trace("[ACTION: SEARCH_TOKENS] [TENANT: {}] [SOURCE: {}] - Fetching hashes by source service.", tenantId, sourceService))
                .doOnError(e -> log.error("[ACTION: SEARCH_TOKENS] [TENANT: {}] [SOURCE: {}] - Error searching hashes: {}", tenantId, sourceService, e.getMessage(), e));
    }

    /**
     * Retrieves filtered hashes for a tenant, source service, and status using reactive pagination.
     *
     * @param tenantId      The isolated tenant context. Must not be null or blank.
     * @param sourceService The originating microservice or upstream system identifier. Must not be null or blank.
     * @param status        The target operational status filter. Must not be null.
     * @param pageable      The pagination and sorting metadata configuration. Must not be null.
     * @return A {@link Flux} stream of matching domain aggregates.
     */
    @Override
    public Flux<HashToken> findAllByTenantIdAndSourceServiceAndStatus(String tenantId, String sourceService, HashStatus status, Pageable pageable) {
        Objects.requireNonNull(tenantId, "Infrastructure constraint violated: Tenant ID is mandatory.");
        Objects.requireNonNull(sourceService, "Infrastructure constraint violated: Source service is mandatory.");
        Objects.requireNonNull(status, "Infrastructure constraint violated: HashStatus filter cannot be null.");
        Objects.requireNonNull(pageable, "Infrastructure constraint violated: Pageable configuration cannot be null.");

        return repository.findByTenantIdAndSourceServiceAndStatus(tenantId, sourceService, status, pageable)
                .map(HashTokenEntity::toDomain)
                .doOnSubscribe(s -> log.trace("[ACTION: SEARCH_TOKENS] [TENANT: {}] [SOURCE: {}] [STATUS: {}] - Fetching hashes by source service and status.", tenantId, sourceService, status))
                .doOnError(e -> log.error("[ACTION: SEARCH_TOKENS] [TENANT: {}] [SOURCE: {}] [STATUS: {}] - Error searching hashes: {}", tenantId, sourceService, status, e.getMessage(), e));
    }
}