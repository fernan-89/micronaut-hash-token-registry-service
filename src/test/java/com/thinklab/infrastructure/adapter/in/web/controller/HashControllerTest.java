package com.thinklab.infrastructure.adapter.in.web.controller;

import com.thinklab.application.port.in.*;
import com.thinklab.application.usecase.command.*;
import com.thinklab.domain.model.HashAudit;
import com.thinklab.domain.model.HashToken;
import com.thinklab.domain.valueobject.HashAlgorithm;
import com.thinklab.domain.valueobject.HashStatus;
import com.thinklab.infrastructure.adapter.in.web.dto.request.*;
import com.thinklab.infrastructure.adapter.in.web.dto.response.*;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 *  Unit Test: Inbound Web Adapter for the Hash Service.
 *  This suite validates the strict mediation between HTTP protocols and Application Use Cases.
 *  It ensures that the Controller correctly translates requests into commands, orchestrates
 *  reactive streams, and projects domain aggregates into sanitized public DTOs.
 *
 *  <p><b>Architectural Principles:</b></p>
 *  <ul>
 *      <li><b>Isolation:</b> Focuses strictly on the Web Layer using Mockito for Use Cases.</li>
 *      <li><b>Reactive Integrity:</b> Verified using {@link StepVerifier} for non-blocking signals.</li>
 *      <li><b>Identity Sovereignty:</b> Aligned with native {@link UUID} enforcement (ADR 005).</li>
 *  </ul>
 *
 *  @version 2.0.0
 */
@ExtendWith(MockitoExtension.class)
class HashControllerTest {

    @Mock private GenerateHashUseCase generateHashUseCase;
    @Mock private GetHashUseCase getHashUseCase;
    @Mock private ListHashesUseCase listHashesUseCase;
    @Mock private SearchHashesUseCase searchHashesUseCase;
    @Mock private DeactivateHashUseCase deactivateHashUseCase;
    @Mock private ReactivateHashUseCase reactivateHashUseCase;
    @Mock private RevokeHashUseCase revokeHashUseCase;
    @Mock private GetAuditLogsUseCase getAuditLogsUseCase;
    @Mock private CountHashesUseCase countHashesUseCase;

    @InjectMocks
    private HashController controller;

    private HashToken dummyToken;
    private final UUID dummyId = UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d");
    private final String tenantId = "TENANT-NASA-PROD-01";

    /**
     * Initializes a valid domain aggregate to prevent NullPointerExceptions during DTO projection.
     */
    @BeforeEach
    void setUp() {
        lenient().when(countHashesUseCase.execute(any(), any(), any())).thenReturn(Mono.just(1L));
        dummyToken = HashToken.create(
                dummyId, tenantId, "mission-control-api", "SEEDED-DATA-2026", "SEEDED-DATA-2026",
                "3f2e1a...f8e9", HashAlgorithm.SHA3_512, "staff-engineer-01"
        );
    }

    /**
     * Validates that POST /hash-token-registry/v1/initiate delegates to the correct UseCase and returns 201 Created.
     */
    @Test
    @DisplayName("POST /hash-token-registry/v1/initiate - Should delegate to GenerateHashUseCase and return 201 CREATED")
    void shouldInitiateHashAndReturn201() {
        // Given: A valid generation request (tenant/source/executor now travel via headers)
        GenerateHashRequest request = new GenerateHashRequest("MISSION-DATA", HashAlgorithm.SHA3_512, false);
        when(generateHashUseCase.execute(any(GenerateHashCommand.class))).thenReturn(Mono.just(dummyToken));

        // When & Then: Execute the controller method and verify the reactive response (DTO id is native UUID)
        StepVerifier.create(controller.initiate(tenantId, "source-api", "admin", request))
                .assertNext(response -> {
                    assertEquals(HttpStatus.CREATED, response.getStatus());
                    assertNotNull(response.body());
                    assertEquals(dummyId, response.body().id());
                })
                .verifyComplete();

        verify(generateHashUseCase, times(1)).execute(any(GenerateHashCommand.class));
    }

    /**
     * Validates that GET /hash-token-registry/v1/{id}/retrieve retrieves a specific token with 200 OK.
     */
    @Test
    @DisplayName("GET /hash-token-registry/v1/{id}/retrieve - Should return 200 OK with projected metadata")
    void shouldRetrieveHashById() {
        // Given: The registry exists
        when(getHashUseCase.execute(any(GetHashQuery.class))).thenReturn(Mono.just(dummyToken));

        // When & Then
        StepVerifier.create(controller.retrieveById(dummyId))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    assertEquals(dummyId, response.body().id());
                })
                .verifyComplete();
    }

    /**
     * Validates the 360° Projection Pattern (ADR 003).
     * Tests parallel aggregation of state and immutable forensic audit trails.
     */
    @Test
    @DisplayName("GET /hash-token-registry/v1/{id}/full-profile/retrieve - Should return aggregated 360° view")
    void shouldRetrieveFullProfileWithAuditTrail() {
        // Given: State and Audit records exist
        HashAudit auditLog = HashAudit.create(
                UUID.randomUUID(), tenantId, dummyId, "GENERATE", "SUCCESS", "system", Map.of()
        );
        when(getHashUseCase.execute(any(GetHashQuery.class))).thenReturn(Mono.just(dummyToken));
        when(getAuditLogsUseCase.execute(dummyId)).thenReturn(Flux.just(auditLog));

        // When & Then: Verify the consolidated parallel projection
        StepVerifier.create(controller.retrieveFullProfile(dummyId))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    assertNotNull(response.body().hash());
                    assertEquals(1, response.body().auditLogs().size());
                })
                .verifyComplete();
    }

    /**
     * Validates that GET /hash-token-registry/v1/retrieve enforces tenant isolation and pagination.
     */
    @Test
    @DisplayName("GET /hash-token-registry/v1/retrieve - Should return paginated stream scoped to tenant")
    void shouldRetrieveAllTenantHashes() {
        // Given: UseCase returns a stream of tokens
        when(listHashesUseCase.execute(any(ListHashesQuery.class))).thenReturn(Flux.just(dummyToken));

        // When & Then
        StepVerifier.create(controller.retrieveAll(tenantId, HashStatus.ACTIVE, 0, 20))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    assertEquals(1, response.body().content().size());
                })
                .verifyComplete();
    }

    /**
     * Validates lifecycle mutation: ACTIVE -> INACTIVE.
     */
    @Test
    @DisplayName("PUT /hash-token-registry/v1/{id}/control/deactivate - Should transition status to INACTIVE")
    void shouldControlDeactivateHash() {
        // Given: Transition results in an inactive token
        HashToken inactiveToken = dummyToken.deactivate("security-admin");
        DeactivateHashRequest request = new DeactivateHashRequest("Maintenance");
        when(deactivateHashUseCase.execute(any(DeactivateHashCommand.class))).thenReturn(Mono.just(inactiveToken));

        // When & Then (DTO status is exposed as String "INACTIVE")
        StepVerifier.create(controller.controlDeactivate(dummyId, "security-admin", request))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    assertEquals("INACTIVE", response.body().status());
                })
                .verifyComplete();
    }

    /**
     * Validates terminal lifecycle event: Irreversible revocation.
     */
    @Test
    @DisplayName("PUT /hash-token-registry/v1/{id}/control/revoke - Should transition to terminal REVOKED state")
    void shouldControlRevokeHashPermanently() {
        // Given: Revocation intent
        HashToken revokedToken = dummyToken.revoke("secops-admin");
        RevokeHashRequest request = new RevokeHashRequest("Security compromise detected by automated monitoring");
        when(revokeHashUseCase.execute(any(RevokeHashCommand.class))).thenReturn(Mono.just(revokedToken));

        // When & Then (DTO status is exposed as Enum HashStatus.REVOKED)
        StepVerifier.create(controller.controlRevoke(dummyId, "secops-admin", request))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    assertEquals(HashStatus.REVOKED, response.body().status());
                })
                .verifyComplete();
    }
}