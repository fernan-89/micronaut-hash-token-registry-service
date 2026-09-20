package com.thinklab.application.interactor;

import com.thinklab.application.port.out.HashTokenRepositoryPort;
import com.thinklab.application.usecase.command.SearchHashesQuery;
import com.thinklab.domain.model.HashToken;
import com.thinklab.domain.valueobject.HashAlgorithm;
import com.thinklab.domain.valueobject.HashStatus;
import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit Test: multi-dimensional search routing (Convention B {@code retrieve/search}). Every filter
 * combination must reach exactly one repository query, always scoped to the tenant.
 */
@ExtendWith(MockitoExtension.class)
class SearchHashesInteractorTest {

    private static final String TENANT = "tenant-prod-alpha-1";

    @Mock private HashTokenRepositoryPort repository;

    private SearchHashesInteractor interactor;
    private HashToken token;

    @BeforeEach
    void setUp() {
        interactor = new SearchHashesInteractor(repository);
        token = HashToken.create(UUID.randomUUID(), TENANT, "order-service", "payload", "payload", "abc123", HashAlgorithm.SHA_256, "admin");
    }

    @Test
    @DisplayName("source service + status routes to the combined query")
    void sourceAndStatus() {
        when(repository.findAllByTenantIdAndSourceServiceAndStatus(eq(TENANT), eq("order-service"), eq(HashStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(Flux.just(token));

        StepVerifier.create(interactor.execute(new SearchHashesQuery(TENANT, "order-service", HashStatus.ACTIVE, 0, 20)))
                .expectNext(token).verifyComplete();

        verify(repository, never()).findAllByTenantId(any(), any());
        verify(repository, never()).findAllByTenantIdAndStatus(any(), any(), any());
        verify(repository, never()).findAllByTenantIdAndSourceService(any(), any(), any());
    }

    @Test
    @DisplayName("source service only routes to the source-service query")
    void sourceOnly() {
        when(repository.findAllByTenantIdAndSourceService(eq(TENANT), eq("order-service"), any(Pageable.class))).thenReturn(Flux.just(token));

        StepVerifier.create(interactor.execute(new SearchHashesQuery(TENANT, "order-service", null, 0, 20)))
                .expectNext(token).verifyComplete();

        verify(repository, never()).findAllByTenantId(any(), any());
    }

    @Test
    @DisplayName("status only routes to the status query")
    void statusOnly() {
        when(repository.findAllByTenantIdAndStatus(eq(TENANT), eq(HashStatus.REVOKED), any(Pageable.class))).thenReturn(Flux.empty());

        StepVerifier.create(interactor.execute(new SearchHashesQuery(TENANT, null, HashStatus.REVOKED, 0, 20))).verifyComplete();

        verify(repository).findAllByTenantIdAndStatus(eq(TENANT), eq(HashStatus.REVOKED), any(Pageable.class));
    }

    @Test
    @DisplayName("no filters route to the plain tenant listing, paginated as requested")
    void noFilters() {
        when(repository.findAllByTenantId(eq(TENANT), any(Pageable.class))).thenReturn(Flux.just(token, token));

        StepVerifier.create(interactor.execute(new SearchHashesQuery(TENANT, null, null, 2, 5))).expectNextCount(2).verifyComplete();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByTenantId(eq(TENANT), captor.capture());
        assertEquals(2, captor.getValue().getNumber());
        assertEquals(5, captor.getValue().getSize());
    }

    @Test
    @DisplayName("a blank source service is normalised away and behaves as no filter")
    void blankSourceIsIgnored() {
        when(repository.findAllByTenantId(eq(TENANT), any(Pageable.class))).thenReturn(Flux.empty());

        StepVerifier.create(interactor.execute(new SearchHashesQuery(TENANT, "   ", null, null, null))).verifyComplete();

        verify(repository).findAllByTenantId(eq(TENANT), any(Pageable.class));
    }

    @Test
    @DisplayName("the query defaults page to 0 and size to 20 and trims tenant and source service")
    void queryDefaultsAndNormalisation() {
        SearchHashesQuery query = new SearchHashesQuery("  " + TENANT + "  ", "  order-service ", null, null, null);

        assertEquals(TENANT, query.tenantId());
        assertEquals("order-service", query.sourceService());
        assertEquals(0, query.page());
        assertEquals(20, query.size());
        assertNull(new SearchHashesQuery(TENANT, "", null, 0, 1).sourceService());
    }

    @Test
    @DisplayName("the query rejects a missing or blank tenant")
    void queryTenantIsolation() {
        assertThrows(NullPointerException.class, () -> new SearchHashesQuery(null, null, null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> new SearchHashesQuery("   ", null, null, 0, 20));
    }

    @Test
    @DisplayName("a repository failure is propagated as an error signal")
    void repositoryFailure() {
        when(repository.findAllByTenantId(eq(TENANT), any(Pageable.class))).thenReturn(Flux.error(new IllegalStateException("mongo down")));

        StepVerifier.create(interactor.execute(new SearchHashesQuery(TENANT, null, null, 0, 20)))
                .expectErrorMessage("mongo down").verify();
    }

    @Test
    @DisplayName("the interactor rejects null collaborators and null queries")
    void nullGuards() {
        assertThrows(NullPointerException.class, () -> new SearchHashesInteractor(null));
        assertThrows(NullPointerException.class, () -> interactor.execute(null));
    }
}
