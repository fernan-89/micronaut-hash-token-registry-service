package com.thinklab.infrastructure.adapter.in.web.controller;

import com.thinklab.application.usecase.command.GetHashQuery;
import com.thinklab.application.usecase.command.ListHashesQuery;
import com.thinklab.application.usecase.command.SearchHashesQuery;
import com.thinklab.application.port.in.*;
import com.thinklab.domain.valueobject.HashStatus;
import com.thinklab.infrastructure.adapter.in.web.dto.request.*;
import com.thinklab.infrastructure.adapter.in.web.dto.response.*;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller: The primary inbound adapter for the {@code hash-token-registry} Service Domain.
 *
 * <p><b>Architectural Role:</b>
 * Acts as a thin, highly cohesive mediation layer between the HTTP transport protocol and the
 * Core Application UseCases (Ports). It focuses exclusively on protocol translation, strict input
 * validation (JSR 380), and secure data projection, ensuring absolute isolation of the domain layer.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b>
 * The {@link com.thinklab.domain.model.HashToken} aggregate is the Control Record of this Service Domain.
 * Every route is expressed as {@code /hash-token-registry/v1/{control-record-id}/{behavior-qualifier}},
 * using the standard Behavior Qualifier vocabulary ({@code initiate}, {@code retrieve}, {@code control})
 * instead of ad-hoc REST verbs. There is no {@code DELETE}: {@code revoke} is a terminal {@code control}
 * transition, never a physical deletion.
 *
 * <p><b>Header-Sourced Forensics (ADR-013):</b>
 * {@code X-Tenant-Id}, {@code X-Source-Service}, and {@code X-Executor} are mandatory headers on every
 * mutating or tenant-scoped operation, replacing the previous body-embedded {@code tenantId}/{@code
 * sourceService}/{@code executor} fields.
 *
 * <p><b>Contractual Obligations:</b>
 * <ul>
 * <li><b>Reactive Purity:</b> 100% non-blocking. Leverages Project Reactor ({@link Mono}) to ensure
 *     the Netty EventLoop is never stalled.</li>
 * <li><b>Identity Sovereignty:</b> Enforces {@link UUID} strictly at the API boundary, automatically
 *     rejecting malformed requests with 400 Bad Request before hitting business logic.</li>
 * <li><b>Constructor Injection:</b> Explicitly avoids Lombok generated constructors (ADR-001) to
 *     guarantee deterministic dependency injection and proxying by Micronaut AOP.</li>
 * </ul>
 *
 * <p><b>Telemetry & Observability:</b>
 * Adheres strictly to the structured logging format: {@code [ACTION: NAME] [ID: UUID]}.
 * Emits signals via Reactor lifecycle hooks ({@code doOnSubscribe}, {@code doOnSuccess}, {@code doOnError})
 * without disrupting the asynchronous data stream.
 *
 * @author Thinklab Systems Engineering Team
 * @version 4.0.0-BIAN
 * @since 1.0
 */
@Slf4j
@Controller("/hash-token-registry/v1")
@Tag(name = "Hash Token Registry", description = "BIAN-aligned Service Domain for generating, querying, and auditing cryptographic hashes under Zero-Trust constraints.")
public class HashController {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String SOURCE_SERVICE_HEADER = "X-Source-Service";
    private static final String EXECUTOR_HEADER = "X-Executor";

    private final GenerateHashUseCase generateHashUseCase;
    private final GetHashUseCase getHashUseCase;
    private final ListHashesUseCase listHashesUseCase;
    private final SearchHashesUseCase searchHashesUseCase;
    private final DeactivateHashUseCase deactivateHashUseCase;
    private final ReactivateHashUseCase reactivateHashUseCase;
    private final RevokeHashUseCase revokeHashUseCase;
    private final GetAuditLogsUseCase getAuditLogsUseCase;

    /**
     * Explicit constructor for strict dependency injection (ADR-001).
     */
    @Inject
    public HashController(
            GenerateHashUseCase generateHashUseCase,
            GetHashUseCase getHashUseCase,
            ListHashesUseCase listHashesUseCase,
            SearchHashesUseCase searchHashesUseCase,
            DeactivateHashUseCase deactivateHashUseCase,
            ReactivateHashUseCase reactivateHashUseCase,
            RevokeHashUseCase revokeHashUseCase,
            GetAuditLogsUseCase getAuditLogsUseCase
    ) {
        this.generateHashUseCase = generateHashUseCase;
        this.getHashUseCase = getHashUseCase;
        this.listHashesUseCase = listHashesUseCase;
        this.searchHashesUseCase = searchHashesUseCase;
        this.deactivateHashUseCase = deactivateHashUseCase;
        this.reactivateHashUseCase = reactivateHashUseCase;
        this.revokeHashUseCase = revokeHashUseCase;
        this.getAuditLogsUseCase = getAuditLogsUseCase;
    }

    /**
     * Behavior Qualifier: {@code initiate}. Generates a new cryptographic hash or serial key.
     */
    @Post("/initiate")
    @Operation(
            summary = "Initiate a new cryptographic hash",
            description = "Calculates a cryptographic hash based on the requested algorithm over the sanitized payload, persists it securely, and registers the initial creation audit event."
    )
    @ApiResponse(responseCode = "201", description = "Hash generated, persisted, and audited successfully.")
    @ApiResponse(responseCode = "400", description = "Invalid request payload, missing headers, or invalid algorithm.")
    @ApiResponse(responseCode = "500", description = "Internal server error during hash calculation or secure persistence sequence.")
    public Mono<MutableHttpResponse<HashResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank @Parameter(in = ParameterIn.HEADER, name = TENANT_HEADER, description = "Isolated tenant context.", required = true, example = "tenant-prod-alpha-1") String tenantId,
            @Header(SOURCE_SERVICE_HEADER) @NotBlank @Parameter(in = ParameterIn.HEADER, name = SOURCE_SERVICE_HEADER, description = "Identification of the upstream system requesting the hash.", required = true, example = "order-management-service") String sourceService,
            @Header(EXECUTOR_HEADER) @NotBlank @Parameter(in = ParameterIn.HEADER, name = EXECUTOR_HEADER, description = "Verified identification of the agent or process executing the action.", required = true, example = "admin-user-01") String executor,
            @Body @Valid @Parameter(description = "Payload and cryptographic specification.", required = true) GenerateHashRequest request
    ) {
        return generateHashUseCase.execute(request.toCommand(tenantId, sourceService, executor))
                .map(HashResponse::fromDomain)
                .map(HttpResponse::created)
                .doOnSubscribe(s -> log.info("[ACTION: INITIATE_HASH] [TENANT: {}] [ALGO: {}] - Initiating entity creation protocol.", tenantId, request.algorithm()))
                .doOnSuccess(res -> log.info("[ACTION: INITIATE_HASH] [TENANT: {}] - Entity creation successfully completed. Status: 201 CREATED.", tenantId))
                .doOnError(err -> log.error("[ACTION: INITIATE_HASH] [TENANT: {}] - Entity creation protocol failed: {}", tenantId, err.getMessage()));
    }

    /**
     * Behavior Qualifier: {@code retrieve}. Fetches a single hash Control Record by UUID.
     */
    @Get("/{id}/retrieve")
    @Operation(
            summary = "Retrieve a hash state by UUID",
            description = "Retrieves the sanitized metadata, current operational state, and algorithm details of a specific hash registry."
    )
    @ApiResponse(responseCode = "200", description = "Hash record found and returned successfully.")
    @ApiResponse(responseCode = "400", description = "Malformed identifier format provided (must comply with UUID canonical standard).")
    @ApiResponse(responseCode = "404", description = "No hash record exists for the provided system identifier (RFC 7807 problem details returned).")
    public Mono<MutableHttpResponse<HashResponse>> retrieveById(
            @PathVariable @Parameter(name = "id", description = "The immutable UUID of the cryptographic hash registry", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d") UUID id
    ) {
        return getHashUseCase.execute(new GetHashQuery(id))
                .map(HashResponse::fromDomain)
                .map(HttpResponse::ok)
                .doOnSubscribe(s -> log.info("[ACTION: RETRIEVE_HASH] [ID: {}] - Initiating secure retrieval of entity metadata and current state.", id))
                .doOnSuccess(res -> log.info("[ACTION: RETRIEVE_HASH] [ID: {}] - Entity metadata successfully retrieved and projected.", id))
                .doOnError(err -> log.error("[ACTION: RETRIEVE_HASH] [ID: {}] - Secure retrieval query failed: {}", id, err.getMessage()));
    }

    /**
     * Behavior Qualifier: {@code retrieve} (collection). Paginated tenant-scoped listing.
     */
    @Get("/retrieve")
    @Operation(
            summary = "Retrieve paginated tenant hashes",
            description = "Returns a paginated stream of hashes belonging strictly to the tenant context declared in the X-Tenant-Id header."
    )
    @ApiResponse(responseCode = "200", description = "Paginated list of hashes retrieved and projected successfully.")
    @ApiResponse(responseCode = "400", description = "Missing or blank X-Tenant-Id header, or invalid pagination range parameters.")
    @ApiResponse(responseCode = "500", description = "Internal server error occurred during multitenant lookup execution.")
    public Mono<MutableHttpResponse<PagedHashResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank @Parameter(in = ParameterIn.HEADER, name = TENANT_HEADER, description = "Isolated tenant context to scope the query.", required = true, example = "tenant-prod-alpha-1") String tenantId,
            @QueryValue @Nullable @Parameter(name = "status", description = "Optional operational state filter to scope the lookup", required = false, example = "ACTIVE") HashStatus status,
            @QueryValue(defaultValue = "0") @Parameter(name = "page", description = "Zero-based index of the target page", schema = @Schema(defaultValue = "0")) int page,
            @QueryValue(defaultValue = "20") @Parameter(name = "size", description = "The maximum volume of records to return in a single page", schema = @Schema(defaultValue = "20")) int size
    ) {
        return listHashesUseCase.execute(new ListHashesQuery(tenantId, status, page, size))
                .map(HashResponse::fromDomain)
                .collectList()
                .map(content -> PagedHashResponse.of(content, 0, page, size))
                .map(HttpResponse::ok)
                .doOnSubscribe(s -> log.info("[ACTION: RETRIEVE_HASHES] [TENANT: {}] [STATUS: {}] [PAGE: {}] [SIZE: {}] - Initiating paginated discovery query.", tenantId, status, page, size))
                .doOnSuccess(res -> log.info("[ACTION: RETRIEVE_HASHES] [TENANT: {}] - Paginated discovery completed successfully. Elements projected: {}", tenantId, res.body() != null ? res.body().content().size() : 0))
                .doOnError(err -> log.error("[ACTION: RETRIEVE_HASHES] [TENANT: {}] - Paginated discovery failed: {}", tenantId, err.getMessage()));
    }

    /**
     * Behavior Qualifier: {@code retrieve/search} (Convention B). Multi-dimensional search by source service and/or status.
     */
    @Get("/retrieve/search")
    @Operation(
            summary = "Search tenant hashes by source service and/or status",
            description = "Convention B behavior qualifier. Returns a paginated stream of hashes belonging to the tenant context, optionally filtered by source service and/or operational status."
    )
    @ApiResponse(responseCode = "200", description = "Paginated search results retrieved and projected successfully.")
    @ApiResponse(responseCode = "400", description = "Missing or blank X-Tenant-Id header, or invalid pagination range parameters.")
    public Mono<MutableHttpResponse<PagedHashResponse>> search(
            @Header(TENANT_HEADER) @NotBlank @Parameter(in = ParameterIn.HEADER, name = TENANT_HEADER, description = "Isolated tenant context to scope the query.", required = true, example = "tenant-prod-alpha-1") String tenantId,
            @QueryValue @Nullable @Parameter(name = "sourceService", description = "Optional originating microservice filter.", required = false, example = "order-management-service") String sourceService,
            @QueryValue @Nullable @Parameter(name = "status", description = "Optional operational state filter.", required = false, example = "ACTIVE") HashStatus status,
            @QueryValue(defaultValue = "0") @Parameter(name = "page", schema = @Schema(defaultValue = "0")) int page,
            @QueryValue(defaultValue = "20") @Parameter(name = "size", schema = @Schema(defaultValue = "20")) int size
    ) {
        return searchHashesUseCase.execute(new SearchHashesQuery(tenantId, sourceService, status, page, size))
                .map(HashResponse::fromDomain)
                .collectList()
                .map(content -> PagedHashResponse.of(content, 0, page, size))
                .map(HttpResponse::ok)
                .doOnSubscribe(s -> log.info("[ACTION: SEARCH_HASHES] [TENANT: {}] [SOURCE: {}] [STATUS: {}] - Initiating multi-dimensional search query.", tenantId, sourceService, status))
                .doOnError(err -> log.error("[ACTION: SEARCH_HASHES] [TENANT: {}] - Search failed: {}", tenantId, err.getMessage()));
    }

    /**
     * Behavior Qualifier: {@code retrieve} scoped by status path segment (Convention B).
     */
    @Get("/status/{status}/retrieve")
    @Operation(
            summary = "Retrieve tenant hashes by status",
            description = "Convention B behavior qualifier. Returns a paginated stream of hashes belonging to the tenant context, filtered by the operational status path segment."
    )
    @ApiResponse(responseCode = "200", description = "Paginated status-scoped list retrieved and projected successfully.")
    @ApiResponse(responseCode = "400", description = "Missing or blank X-Tenant-Id header, or invalid status/pagination parameters.")
    public Mono<MutableHttpResponse<PagedHashResponse>> retrieveByStatus(
            @PathVariable @Parameter(name = "status", description = "The operational status to scope the lookup.", required = true, example = "ACTIVE") HashStatus status,
            @Header(TENANT_HEADER) @NotBlank @Parameter(in = ParameterIn.HEADER, name = TENANT_HEADER, description = "Isolated tenant context to scope the query.", required = true, example = "tenant-prod-alpha-1") String tenantId,
            @QueryValue(defaultValue = "0") @Parameter(name = "page", schema = @Schema(defaultValue = "0")) int page,
            @QueryValue(defaultValue = "20") @Parameter(name = "size", schema = @Schema(defaultValue = "20")) int size
    ) {
        return listHashesUseCase.execute(new ListHashesQuery(tenantId, status, page, size))
                .map(HashResponse::fromDomain)
                .collectList()
                .map(content -> PagedHashResponse.of(content, 0, page, size))
                .map(HttpResponse::ok)
                .doOnSubscribe(s -> log.info("[ACTION: RETRIEVE_HASHES_BY_STATUS] [TENANT: {}] [STATUS: {}] - Initiating status-scoped discovery query.", tenantId, status))
                .doOnError(err -> log.error("[ACTION: RETRIEVE_HASHES_BY_STATUS] [TENANT: {}] [STATUS: {}] - Discovery failed: {}", tenantId, status, err.getMessage()));
    }

    /**
     * Behavior Qualifier: {@code control/deactivate}. Suspends a hash's operational status (reversible).
     */
    @Put("/{id}/control/deactivate")
    @Operation(
            summary = "Control: suspend hash operation (deactivate)",
            description = "Transitions an ACTIVE hash to an INACTIVE status. This operation is non-destructive, fully reversible, and recorded in the audit trail."
    )
    @ApiResponse(responseCode = "200", description = "Hash successfully transitioned to INACTIVE state and audited.")
    @ApiResponse(responseCode = "400", description = "Invalid request payload, missing X-Executor header, or malformed UUID parameter.")
    @ApiResponse(responseCode = "404", description = "No hash record exists for the provided system identifier.")
    @ApiResponse(responseCode = "409", description = "State transition conflict: hash is already INACTIVE or permanently REVOKED.")
    public Mono<MutableHttpResponse<DeactivateHashResponse>> controlDeactivate(
            @PathVariable @Parameter(name = "id", description = "The immutable UUID of the target hash registry", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d") UUID id,
            @Header(EXECUTOR_HEADER) @NotBlank @Parameter(in = ParameterIn.HEADER, name = EXECUTOR_HEADER, description = "Verified identification of the agent executing the action.", required = true, example = "security-officer-42") String executor,
            @Body @Valid @Parameter(description = "Deactivation justification payload.", required = true) DeactivateHashRequest request
    ) {
        return deactivateHashUseCase.execute(request.toCommand(id, executor))
                .map(token -> DeactivateHashResponse.fromDomain(token, executor, request.reason()))
                .map(HttpResponse::ok)
                .doOnSubscribe(s -> log.info("[ACTION: CONTROL_DEACTIVATE_HASH] [ID: {}] [EXECUTOR: {}] - Initiating status suspension. Reason: {}", id, executor, request.reason()))
                .doOnSuccess(res -> log.info("[ACTION: CONTROL_DEACTIVATE_HASH] [ID: {}] - Entity status successfully transitioned to INACTIVE.", id))
                .doOnError(err -> log.error("[ACTION: CONTROL_DEACTIVATE_HASH] [ID: {}] - Deactivation sequence failed: {}", id, err.getMessage()));
    }

    /**
     * Behavior Qualifier: {@code control/reactivate}. Restores an inactive hash to ACTIVE.
     */
    @Put("/{id}/control/reactivate")
    @Operation(
            summary = "Control: restore hash operation (reactivate)",
            description = "Restores an INACTIVE hash registry back to its ACTIVE operational state. Fails if the hash is already active or terminal."
    )
    @ApiResponse(responseCode = "200", description = "Hash successfully restored to ACTIVE state and audited.")
    @ApiResponse(responseCode = "400", description = "Invalid request payload, missing X-Executor header, or malformed UUID parameter.")
    @ApiResponse(responseCode = "404", description = "No hash record exists for the provided system identifier.")
    @ApiResponse(responseCode = "409", description = "State transition conflict: hash cannot be reactivated.")
    public Mono<MutableHttpResponse<HashResponse>> controlReactivate(
            @PathVariable @Parameter(name = "id", description = "The immutable UUID of the target hash registry", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d") UUID id,
            @Header(EXECUTOR_HEADER) @NotBlank @Parameter(in = ParameterIn.HEADER, name = EXECUTOR_HEADER, description = "Verified identification of the agent executing the restoration action.", required = true, example = "security-officer-01") String executor,
            @Body @Valid @Parameter(description = "Reactivation justification payload.", required = true) ReactivateHashRequest request
    ) {
        return reactivateHashUseCase.execute(request.toCommand(id, executor))
                .map(HashResponse::fromDomain)
                .map(HttpResponse::ok)
                .doOnSubscribe(s -> log.info("[ACTION: CONTROL_REACTIVATE_HASH] [ID: {}] [EXECUTOR: {}] - Initiating status restoration.", id, executor))
                .doOnSuccess(res -> log.info("[ACTION: CONTROL_REACTIVATE_HASH] [ID: {}] - Entity status successfully restored to ACTIVE.", id))
                .doOnError(err -> log.error("[ACTION: CONTROL_REACTIVATE_HASH] [ID: {}] - Reactivation sequence failed: {}", id, err.getMessage()));
    }

    /**
     * Behavior Qualifier: {@code control/revoke}. Terminal, irreversible revocation (idempotent PUT, no DELETE).
     */
    @Put("/{id}/control/revoke")
    @Operation(
            summary = "Control: permanently revoke a hash",
            description = "Irreversibly transitions a hash registry to the terminal REVOKED state. Destructive operation that blocks future mutations. Never a physical deletion."
    )
    @ApiResponse(responseCode = "200", description = "Hash permanently revoked and audited successfully.")
    @ApiResponse(responseCode = "400", description = "Invalid revocation payload, missing X-Executor header, or malformed UUID parameter.")
    @ApiResponse(responseCode = "404", description = "No hash record exists for the provided system identifier.")
    @ApiResponse(responseCode = "409", description = "State conflict: hash is already in a terminal REVOKED state.")
    public Mono<MutableHttpResponse<HashResponse>> controlRevoke(
            @PathVariable @Parameter(name = "id", description = "The immutable UUID of the target hash registry", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d") UUID id,
            @Header(EXECUTOR_HEADER) @NotBlank @Parameter(in = ParameterIn.HEADER, name = EXECUTOR_HEADER, description = "Verified identification of the highly privileged agent executing the destructive action.", required = true, example = "security-admin-01") String executor,
            @Body @Valid @Parameter(description = "Revocation justification payload.", required = true) RevokeHashRequest request
    ) {
        return revokeHashUseCase.execute(request.toCommand(id, executor))
                .map(HashResponse::fromDomain)
                .map(HttpResponse::ok)
                .doOnSubscribe(s -> log.warn("[ACTION: CONTROL_REVOKE_HASH] [ID: {}] [EXECUTOR: {}] - CRITICAL: Initiating permanent and irreversible entity revocation. Reason: {}", id, executor, request.reason()))
                .doOnSuccess(res -> log.warn("[ACTION: CONTROL_REVOKE_HASH] [ID: {}] - CRITICAL: Entity permanently transitioned to terminal REVOKED state.", id))
                .doOnError(err -> log.error("[ACTION: CONTROL_REVOKE_HASH] [ID: {}] - CRITICAL: Revocation sequence aborted due to failure: {}", id, err.getMessage()));
    }

    /**
     * Behavior Qualifier: {@code audit-log/retrieve}. Immutable forensic audit trail.
     */
    @Get("/{id}/audit-log/retrieve")
    @Operation(
            summary = "Retrieve forensic audit trail",
            description = "Retrieves the complete immutable forensic history of state mutations, deactivations, reactivations, or revocations for a specific hash."
    )
    @ApiResponse(responseCode = "200", description = "Audit trail successfully found and projected.")
    @ApiResponse(responseCode = "400", description = "Invalid or malformed UUID identifier format.")
    @ApiResponse(responseCode = "404", description = "No audit log history exists for the provided identifier.")
    public Mono<MutableHttpResponse<List<HashAuditResponse>>> retrieveAuditLog(
            @PathVariable @Parameter(name = "id", description = "The deterministic entity UUID (Hash ID) to fetch forensic history for", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d") UUID id
    ) {
        return getAuditLogsUseCase.execute(id)
                .map(HashAuditResponse::fromDomain)
                .collectList()
                .map(HttpResponse::ok)
                .doOnSubscribe(s -> log.info("[ACTION: RETRIEVE_AUDIT_LOG] [ID: {}] - Initiating extraction of immutable forensic state mutations.", id))
                .doOnSuccess(res -> log.info("[ACTION: RETRIEVE_AUDIT_LOG] [ID: {}] - Forensic trail successfully extracted. Total historical events projected: {}", id, res.body() != null ? res.body().size() : 0))
                .doOnError(err -> log.error("[ACTION: RETRIEVE_AUDIT_LOG] [ID: {}] - Forensic trail extraction failed: {}", id, err.getMessage()));
    }

    /**
     * Behavior Qualifier: {@code full-profile/retrieve} (ADR-003). Consolidated 360-degree view.
     */
    @Get("/{id}/full-profile/retrieve")
    @Operation(
            summary = "Retrieve 360-degree aggregated hash view",
            description = "Implementation of ADR 003. Consolidates the current operational state of a hash registry with its full chronological forensic audit trail in a single parallel projection."
    )
    @ApiResponse(responseCode = "200", description = "Consolidated 360-degree projection successfully materialized.")
    @ApiResponse(responseCode = "400", description = "Invalid or malformed UUID identifier format.")
    @ApiResponse(responseCode = "404", description = "No hash record exists for the provided system identifier.")
    public Mono<MutableHttpResponse<HashFullResponse>> retrieveFullProfile(
            @PathVariable @Parameter(name = "id", description = "The deterministic entity UUID (Hash ID)", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d") UUID id
    ) {
        return Mono.zip(
                        getHashUseCase.execute(new GetHashQuery(id)),
                        getAuditLogsUseCase.execute(id).collectList()
                )
                .map(tuple -> new HashFullResponse(
                        HashResponse.fromDomain(tuple.getT1()),
                        tuple.getT2().stream().map(HashAuditResponse::fromDomain).toList()
                ))
                .map(HttpResponse::ok)
                .doOnSubscribe(s -> log.info("[ACTION: RETRIEVE_FULL_PROFILE] [ID: {}] - Initiating 360-degree projection.", id))
                .doOnSuccess(res -> log.info("[ACTION: RETRIEVE_FULL_PROFILE] [ID: {}] - 360-degree projection successfully materialized.", id))
                .doOnError(err -> log.error("[ACTION: RETRIEVE_FULL_PROFILE] [ID: {}] - 360-degree projection failed: {}", id, err.getMessage()));
    }
}
