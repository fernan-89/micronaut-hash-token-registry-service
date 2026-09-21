package com.thinklab.infrastructure.adapter.in.web.controller;

import com.thinklab.application.port.in.CountHashesUseCase;
import com.thinklab.application.port.in.DeactivateHashUseCase;
import com.thinklab.application.port.in.GenerateHashUseCase;
import com.thinklab.application.port.in.GetAuditLogsUseCase;
import com.thinklab.application.port.in.GetHashUseCase;
import com.thinklab.application.port.in.ListHashesUseCase;
import com.thinklab.application.port.in.ReactivateHashUseCase;
import com.thinklab.application.port.in.RevokeHashUseCase;
import com.thinklab.application.port.in.SearchHashesUseCase;
import com.thinklab.domain.model.HashAudit;
import com.thinklab.domain.model.HashToken;
import com.thinklab.domain.valueobject.HashAlgorithm;
import com.thinklab.domain.valueobject.HashStatus;
import com.thinklab.infrastructure.adapter.in.web.dto.request.DeactivateHashRequest;
import com.thinklab.infrastructure.adapter.in.web.dto.request.GenerateHashRequest;
import com.thinklab.infrastructure.adapter.in.web.dto.request.ReactivateHashRequest;
import com.thinklab.infrastructure.adapter.in.web.dto.request.RevokeHashRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Success and error signals of every endpoint, so the reactive logging hooks and projections are all exercised. */
class HashControllerSignalsTest {

    private static final String TENANT = "tenant-1";
    private static final RuntimeException BOOM = new IllegalStateException("boom");

    private final GenerateHashUseCase generate = mock(GenerateHashUseCase.class);
    private final GetHashUseCase get = mock(GetHashUseCase.class);
    private final ListHashesUseCase list = mock(ListHashesUseCase.class);
    private final SearchHashesUseCase search = mock(SearchHashesUseCase.class);
    private final DeactivateHashUseCase deactivate = mock(DeactivateHashUseCase.class);
    private final ReactivateHashUseCase reactivate = mock(ReactivateHashUseCase.class);
    private final RevokeHashUseCase revoke = mock(RevokeHashUseCase.class);
    private final GetAuditLogsUseCase audits = mock(GetAuditLogsUseCase.class);
    private final CountHashesUseCase count = mock(CountHashesUseCase.class);
    private HashController controller;
    private HashToken token;
    private HashAudit audit;

    @BeforeEach
    void setUp() {
        controller = new HashController(generate, get, list, search, deactivate, reactivate, revoke, audits, count);
        when(count.execute(any(), any(), any())).thenReturn(Mono.just(7L));
        token = HashToken.create(UUID.randomUUID(), TENANT, "svc", "payload", "payload", "deadbeef", HashAlgorithm.SHA_256, "admin");
        audit = HashAudit.create(UUID.randomUUID(), TENANT, token.id(), "OP", "SUCCESS", "admin", Map.of());
    }

    @Test
    @DisplayName("initiate propagates a use-case failure")
    void initiateError() {
        when(generate.execute(any())).thenReturn(Mono.error(BOOM));

        StepVerifier.create(controller.initiate(TENANT, "svc", "admin", new GenerateHashRequest("payload", HashAlgorithm.SHA_256, false)))
                .expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("retrieveById propagates a use-case failure")
    void retrieveByIdError() {
        when(get.execute(any())).thenReturn(Mono.error(BOOM));

        StepVerifier.create(controller.retrieveById(token.id())).expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("retrieveAll projects the page and propagates failures")
    void retrieveAll() {
        when(list.execute(any())).thenReturn(Flux.just(token)).thenReturn(Flux.error(BOOM));

        StepVerifier.create(controller.retrieveAll(TENANT, null, 0, 20))
                .assertNext(r -> {
                    assertEquals(1, r.body().content().size());
                    assertEquals(7L, r.body().totalElements());
                }).verifyComplete();
        StepVerifier.create(controller.retrieveAll(TENANT, null, 0, 20)).expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("search projects the page and propagates failures")
    void search() {
        when(search.execute(any())).thenReturn(Flux.just(token)).thenReturn(Flux.error(BOOM));

        StepVerifier.create(controller.search(TENANT, "svc", HashStatus.ACTIVE, 0, 20))
                .assertNext(r -> {
                    assertEquals(1, r.body().content().size());
                    assertEquals(7L, r.body().totalElements());
                }).verifyComplete();
        StepVerifier.create(controller.search(TENANT, null, null, 0, 20)).expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("retrieveByStatus projects the page and propagates failures")
    void retrieveByStatus() {
        when(list.execute(any())).thenReturn(Flux.just(token)).thenReturn(Flux.error(BOOM));

        StepVerifier.create(controller.retrieveByStatus(HashStatus.ACTIVE, TENANT, 0, 20))
                .assertNext(r -> {
                    assertEquals(1, r.body().content().size());
                    assertEquals(7L, r.body().totalElements());
                }).verifyComplete();
        StepVerifier.create(controller.retrieveByStatus(HashStatus.ACTIVE, TENANT, 0, 20)).expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("deactivate succeeds and propagates failures")
    void deactivate() {
        when(deactivate.execute(any())).thenReturn(Mono.just(token.deactivate("op"))).thenReturn(Mono.error(BOOM));
        DeactivateHashRequest request = new DeactivateHashRequest("valid reason");

        StepVerifier.create(controller.controlDeactivate(token.id(), "op", request))
                .assertNext(r -> assertEquals("INACTIVE", r.body().status())).verifyComplete();
        StepVerifier.create(controller.controlDeactivate(token.id(), "op", request)).expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("reactivate succeeds and propagates failures")
    void reactivate() {
        when(reactivate.execute(any())).thenReturn(Mono.just(token)).thenReturn(Mono.error(BOOM));
        ReactivateHashRequest request = new ReactivateHashRequest("valid reason");

        StepVerifier.create(controller.controlReactivate(token.id(), "op", request))
                .assertNext(r -> assertEquals(token.id(), r.body().id())).verifyComplete();
        StepVerifier.create(controller.controlReactivate(token.id(), "op", request)).expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("revoke succeeds and propagates failures")
    void revoke() {
        when(revoke.execute(any())).thenReturn(Mono.just(token.revoke("op"))).thenReturn(Mono.error(BOOM));
        RevokeHashRequest request = new RevokeHashRequest("a sufficiently long reason");

        StepVerifier.create(controller.controlRevoke(token.id(), "op", request))
                .assertNext(r -> assertEquals(HashStatus.REVOKED, r.body().status())).verifyComplete();
        StepVerifier.create(controller.controlRevoke(token.id(), "op", request)).expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("audit log succeeds and propagates failures")
    void auditLog() {
        when(audits.execute(any())).thenReturn(Flux.just(audit)).thenReturn(Flux.error(BOOM));

        StepVerifier.create(controller.retrieveAuditLog(token.id()))
                .assertNext(r -> assertEquals(1, r.body().size())).verifyComplete();
        StepVerifier.create(controller.retrieveAuditLog(token.id())).expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("full profile propagates a failure from either source")
    void fullProfileErrors() {
        when(get.execute(any())).thenReturn(Mono.error(BOOM));
        when(audits.execute(any())).thenReturn(Flux.empty());

        StepVerifier.create(controller.retrieveFullProfile(token.id())).expectError(IllegalStateException.class).verify();
    }
}
