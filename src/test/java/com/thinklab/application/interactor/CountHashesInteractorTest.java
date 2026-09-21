package com.thinklab.application.interactor;

import com.thinklab.application.port.out.HashTokenRepositoryPort;
import com.thinklab.domain.valueobject.HashStatus;
import com.thinklab.infrastructure.adapter.out.mongo.HashTokenRepositoryAdapter;
import com.thinklab.infrastructure.adapter.out.mongo.repository.HashTokenMongoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CountHashesInteractorTest {

    private final HashTokenRepositoryPort port = mock(HashTokenRepositoryPort.class);

    @Test
    @DisplayName("the interactor trims the tenant and delegates the filters to the port")
    void delegates() {
        when(port.countByFilters("tenant-1", "svc", HashStatus.ACTIVE)).thenReturn(Mono.just(3L));

        StepVerifier.create(new CountHashesInteractor(port).execute(" tenant-1 ", "svc", HashStatus.ACTIVE))
                .expectNext(3L).verifyComplete();
    }

    @Test
    @DisplayName("constructor and tenant are null-checked")
    void guards() {
        assertThrows(NullPointerException.class, () -> new CountHashesInteractor(null));
        assertThrows(NullPointerException.class, () -> new CountHashesInteractor(port).execute(null, null, null));
    }

    @Test
    @DisplayName("the adapter dispatches each filter combination to the matching count query")
    void adapterDispatch() {
        HashTokenMongoRepository repository = mock(HashTokenMongoRepository.class);
        when(repository.countByTenantId("t")).thenReturn(Mono.just(1L));
        when(repository.countByTenantIdAndStatus("t", HashStatus.ACTIVE)).thenReturn(Mono.just(2L));
        when(repository.countByTenantIdAndSourceService("t", "s")).thenReturn(Mono.just(3L));
        when(repository.countByTenantIdAndSourceServiceAndStatus("t", "s", HashStatus.ACTIVE)).thenReturn(Mono.just(4L));
        HashTokenRepositoryAdapter adapter = new HashTokenRepositoryAdapter(repository);

        StepVerifier.create(adapter.countByFilters("t", null, null)).expectNext(1L).verifyComplete();
        StepVerifier.create(adapter.countByFilters("t", null, HashStatus.ACTIVE)).expectNext(2L).verifyComplete();
        StepVerifier.create(adapter.countByFilters("t", "s", null)).expectNext(3L).verifyComplete();
        StepVerifier.create(adapter.countByFilters("t", "s", HashStatus.ACTIVE)).expectNext(4L).verifyComplete();
    }

    @Test
    @DisplayName("the adapter validates the tenant and surfaces driver failures")
    void adapterGuardsAndErrors() {
        HashTokenMongoRepository repository = mock(HashTokenMongoRepository.class);
        when(repository.countByTenantId("t")).thenReturn(Mono.error(new IllegalStateException("mongo down")));
        HashTokenRepositoryAdapter adapter = new HashTokenRepositoryAdapter(repository);

        assertThrows(NullPointerException.class, () -> adapter.countByFilters(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> adapter.countByFilters(" ", null, null));
        StepVerifier.create(adapter.countByFilters("t", null, null)).expectError(IllegalStateException.class).verify();
    }
}
