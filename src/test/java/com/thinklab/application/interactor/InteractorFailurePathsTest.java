package com.thinklab.application.interactor;

import com.thinklab.application.port.out.HashAuditRepositoryPort;
import com.thinklab.application.port.out.HashTokenRepositoryPort;
import com.thinklab.application.usecase.command.DeactivateHashCommand;
import com.thinklab.application.usecase.command.ListHashesQuery;
import com.thinklab.application.usecase.command.ReactivateHashCommand;
import com.thinklab.application.usecase.command.RevokeHashCommand;
import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** System-failure branches of the lifecycle interactors (anything that is not a plain not-found). */
class InteractorFailurePathsTest {

    private final HashTokenRepositoryPort tokens = mock(HashTokenRepositoryPort.class);
    private final HashAuditRepositoryPort audits = mock(HashAuditRepositoryPort.class);
    private final UUID id = UUID.randomUUID();

    @Test
    @DisplayName("deactivate surfaces a repository failure as an error signal")
    void deactivateSystemFailure() {
        when(tokens.findById(id)).thenReturn(Mono.error(new IllegalStateException("mongo down")));

        StepVerifier.create(new DeactivateHashInteractor(tokens, audits).execute(new DeactivateHashCommand(id, "op", "valid reason")))
                .expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("reactivate surfaces a repository failure as an error signal")
    void reactivateSystemFailure() {
        when(tokens.findById(id)).thenReturn(Mono.error(new IllegalStateException("mongo down")));

        StepVerifier.create(new ReactivateHashInteractor(tokens, audits).execute(new ReactivateHashCommand(id, "op", "valid reason")))
                .expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("revoke surfaces a repository failure as an error signal")
    void revokeSystemFailure() {
        when(tokens.findById(id)).thenReturn(Mono.error(new IllegalStateException("mongo down")));

        StepVerifier.create(new RevokeHashInteractor(tokens, audits).execute(new RevokeHashCommand(id, "op", "a sufficiently long reason")))
                .expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("listing without a status filter surfaces a repository failure")
    void listFullRetrievalFailure() {
        when(tokens.findAllByTenantId(any(), any(Pageable.class))).thenReturn(Flux.error(new IllegalStateException("mongo down")));

        StepVerifier.create(new ListHashesInteractor(tokens).execute(new ListHashesQuery("tenant-1", null, 0, 10)))
                .expectError(IllegalStateException.class).verify();
    }
}
