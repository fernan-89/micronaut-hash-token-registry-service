package com.thinklab.infrastructure.adapter.out.mongo.repository;

import com.thinklab.domain.valueobject.HashStatus;
import com.thinklab.infrastructure.adapter.out.mongo.entity.HashTokenEntity;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Infrastructure Adapter: Reactive repository for {@link HashTokenEntity} persistence.
 *
 * <p><b>Architectural Role:</b>
 * This interface implements the low-level infrastructure persistence contract for the Hash Registry.
 * It leverages Micronaut Data's Ahead-of-Time (AOT) compilation to generate reflection-free,
 * ultra-high performance non-blocking query routines for MongoDB.
 *
 * <p><b>Contractual Obligations:</b>
 * <ul>
 * <li><b>Non-Blocking I/O:</b> Extends {@link ReactorCrudRepository} to natively support Project Reactor
 *     streams ({@link Mono} and {@link Flux}), guaranteeing that Netty EventLoops are never blocked by I/O.</li>
 * <li><b>Identity Sovereignty:</b> Enforces {@link UUID} primary keys to utilize native BSON Binary
 *     (Subtype 4) indexing and storage optimization in MongoDB.</li>
 * <li><b>Persistence Resilience (TD-004):</b> Existence checks return reactive booleans. Developers must
 *     chain {@code .defaultIfEmpty(false)} where appropriate to prevent pipeline collapse on empty cursors.</li>
 * </ul>
 *
 * @author ThinkLab
 * @version 1.0.0
 * @since 1.0
 */
@MongoRepository
public interface HashTokenMongoRepository extends ReactorCrudRepository<HashTokenEntity, UUID> {

    /**
     * Verifies the existence of a matching hash for the given tenant and raw payload based on status.
     *
     * <p><b>Resilience Note (TD-004):</b> Depending on the MongoDB driver version, boolean existence queries
     * may emit an empty signal if no document matches. Ensure downstream consumers protect the stream
     * using {@code .defaultIfEmpty(false)}.
     *
     * @param tenantId The isolated tenant boundary identifier. Must not be null or blank.
     * @param payload  The original raw payload string. Must not be null.
     * @param status   The target operational status filter (e.g., ACTIVE). Must not be null.
     * @return A {@link Mono} emitting {@code true} if a matching document exists; {@code false} otherwise.
     */
    Mono<Boolean> existsByTenantIdAndPayloadAndStatus(
            String tenantId,
            String payload,
            HashStatus status
    );

    /**
     * Verifies the existence of a matching hash for the given tenant and generated cryptographic value.
     *
     * <p><b>Resilience Note (TD-004):</b> Used for business-level collision prevention. Chain
     * {@code .defaultIfEmpty(false)} to safeguard the reactive pipeline from unexpected empty emissions.
     *
     * @param tenantId      The isolated tenant boundary identifier. Must not be null or blank.
     * @param generatedHash The calculated cryptographic hash string. Must not be null.
     * @param status        The target operational status filter (e.g., ACTIVE). Must not be null.
     * @return A {@link Mono} emitting {@code true} if a matching document exists; {@code false} otherwise.
     */
    Mono<Boolean> existsByTenantIdAndGeneratedHashAndStatus(
            String tenantId,
            String generatedHash,
            HashStatus status
    );

    /**
     * Retrieves a paginated reactive stream of hash entities filtered strictly by tenant and operational status.
     *
     * @param tenantId The isolated tenant boundary identifier. Must not be null or blank.
     * @param status   The target operational status filter. Must not be null.
     * @param pageable The pagination and sorting metadata configuration. Must not be null.
     * @return A {@link Flux} emitting matching hash persistence entities.
     */
    Flux<HashTokenEntity> findByTenantIdAndStatus(
            String tenantId,
            HashStatus status,
            Pageable pageable
    );

    /**
     * Retrieves a paginated reactive stream of all hash entities associated with a specific tenant boundary.
     *
     * @param tenantId The isolated tenant boundary identifier. Must not be null or blank.
     * @param pageable The pagination and sorting metadata configuration. Must not be null.
     * @return A {@link Flux} emitting the tenant's hash persistence entities.
     */
    Flux<HashTokenEntity> findByTenantId(
            String tenantId,
            Pageable pageable
    );

    /**
     * Retrieves a paginated reactive stream of hash entities filtered by tenant and source service.
     *
     * @param tenantId      The isolated tenant boundary identifier. Must not be null or blank.
     * @param sourceService The originating microservice or upstream system identifier. Must not be null or blank.
     * @param pageable      The pagination and sorting metadata configuration. Must not be null.
     * @return A {@link Flux} emitting matching hash persistence entities.
     */
    Flux<HashTokenEntity> findByTenantIdAndSourceService(
            String tenantId,
            String sourceService,
            Pageable pageable
    );

    /**
     * Retrieves a paginated reactive stream of hash entities filtered by tenant, source service, and status.
     *
     * @param tenantId      The isolated tenant boundary identifier. Must not be null or blank.
     * @param sourceService The originating microservice or upstream system identifier. Must not be null or blank.
     * @param status        The target operational status filter. Must not be null.
     * @param pageable      The pagination and sorting metadata configuration. Must not be null.
     * @return A {@link Flux} emitting matching hash persistence entities.
     */
    Mono<Long> countByTenantId(String tenantId);

    Mono<Long> countByTenantIdAndStatus(String tenantId, HashStatus status);

    Mono<Long> countByTenantIdAndSourceService(String tenantId, String sourceService);

    Mono<Long> countByTenantIdAndSourceServiceAndStatus(String tenantId, String sourceService, HashStatus status);

    Flux<HashTokenEntity> findByTenantIdAndSourceServiceAndStatus(
            String tenantId,
            String sourceService,
            HashStatus status,
            Pageable pageable
    );
}