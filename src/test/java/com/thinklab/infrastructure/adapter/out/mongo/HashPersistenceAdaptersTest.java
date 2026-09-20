package com.thinklab.infrastructure.adapter.out.mongo;

import com.thinklab.domain.model.HashAudit;
import com.thinklab.domain.model.HashToken;
import com.thinklab.domain.valueobject.HashAlgorithm;
import com.thinklab.domain.valueobject.HashStatus;
import com.thinklab.infrastructure.adapter.out.mongo.entity.HashAuditEntity;
import com.thinklab.infrastructure.adapter.out.mongo.entity.HashTokenEntity;
import com.thinklab.infrastructure.adapter.out.mongo.repository.HashAuditMongoRepository;
import com.thinklab.infrastructure.adapter.out.mongo.repository.HashTokenMongoRepository;
import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers both Micronaut Data adapters and their entity mappings. */
@ExtendWith(MockitoExtension.class)
class HashPersistenceAdaptersTest {

    private static final String TENANT = "tenant-1";

    @Mock private HashTokenMongoRepository tokenRepository;
    @Mock private HashAuditMongoRepository auditRepository;

    private HashTokenRepositoryAdapter tokenAdapter;
    private HashAuditRepositoryAdapter auditAdapter;
    private HashToken token;
    private HashAudit audit;
    private Pageable page;

    @BeforeEach
    void setUp() {
        tokenAdapter = new HashTokenRepositoryAdapter(tokenRepository);
        auditAdapter = new HashAuditRepositoryAdapter(auditRepository);
        token = HashToken.create(UUID.randomUUID(), TENANT, "svc", "payload", "payload", "deadbeef", HashAlgorithm.SHA_256, "admin");
        audit = HashAudit.create(UUID.randomUUID(), TENANT, token.id(), "CREATE", "SUCCESS", "admin", Map.of("k", "v"));
        page = Pageable.from(0, 20);
    }

    // ------------------------------------------------------------ entity mapping

    @Test
    @DisplayName("HashTokenEntity round-trips the aggregate")
    void tokenEntityRoundTrip() {
        HashToken restored = HashTokenEntity.fromDomain(token).toDomain();

        assertEquals(token, restored);
    }

    @Test
    @DisplayName("HashAuditEntity round-trips the audit record including its metadata")
    void auditEntityRoundTrip() {
        HashAudit restored = HashAuditEntity.fromDomain(audit).toDomain();

        assertEquals(audit, restored);
        assertEquals(Map.of("k", "v"), restored.metadata());
    }

    // ------------------------------------------------------------ token adapter

    @Test
    @DisplayName("token adapter: constructor and every method reject null arguments")
    void tokenNullGuards() {
        assertThrows(NullPointerException.class, () -> new HashTokenRepositoryAdapter(null));
        assertThrows(NullPointerException.class, () -> tokenAdapter.save(null));
        assertThrows(NullPointerException.class, () -> tokenAdapter.update(null));
        assertThrows(NullPointerException.class, () -> tokenAdapter.findById(null));
        assertThrows(NullPointerException.class, () -> tokenAdapter.existsActiveByTenantAndPayload(null, "p"));
        assertThrows(NullPointerException.class, () -> tokenAdapter.existsActiveByTenantAndPayload(TENANT, null));
        assertThrows(IllegalArgumentException.class, () -> tokenAdapter.existsActiveByTenantAndPayload(" ", "p"));
        assertThrows(NullPointerException.class, () -> tokenAdapter.findAllByTenantId(null, page));
        assertThrows(NullPointerException.class, () -> tokenAdapter.findAllByTenantId(TENANT, null));
        assertThrows(IllegalArgumentException.class, () -> tokenAdapter.findAllByTenantId(" ", page));
        assertThrows(NullPointerException.class, () -> tokenAdapter.findAllByTenantIdAndStatus(TENANT, null, page));
        assertThrows(IllegalArgumentException.class, () -> tokenAdapter.findAllByTenantIdAndStatus(" ", HashStatus.ACTIVE, page));
        assertThrows(NullPointerException.class, () -> tokenAdapter.findAllByTenantIdAndSourceService(TENANT, null, page));
        assertThrows(NullPointerException.class, () -> tokenAdapter.findAllByTenantIdAndSourceServiceAndStatus(TENANT, "s", null, page));
    }

    @Test
    @DisplayName("token adapter: save and update map through the entity and back")
    void tokenSaveAndUpdate() {
        when(tokenRepository.save(any(HashTokenEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(tokenRepository.update(any(HashTokenEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(tokenAdapter.save(token)).expectNext(token).verifyComplete();
        StepVerifier.create(tokenAdapter.update(token.deactivate("ops"))).expectNextMatches(t -> t.status() == HashStatus.INACTIVE).verifyComplete();
    }

    @Test
    @DisplayName("token adapter: save propagates a repository failure")
    void tokenSaveFailure() {
        when(tokenRepository.save(any(HashTokenEntity.class))).thenReturn(Mono.error(new IllegalStateException("mongo down")));

        StepVerifier.create(tokenAdapter.save(token)).expectErrorMessage("mongo down").verify();
    }

    @Test
    @DisplayName("token adapter: findById maps the entity or completes empty")
    void tokenFindById() {
        when(tokenRepository.findById(token.id())).thenReturn(Mono.just(HashTokenEntity.fromDomain(token))).thenReturn(Mono.empty());

        StepVerifier.create(tokenAdapter.findById(token.id())).expectNext(token).verifyComplete();
        StepVerifier.create(tokenAdapter.findById(token.id())).verifyComplete();
    }

    @Test
    @DisplayName("token adapter: the duplicate check only considers ACTIVE tokens and defaults empty to false")
    void tokenExists() {
        when(tokenRepository.existsByTenantIdAndPayloadAndStatus(TENANT, "p1", HashStatus.ACTIVE)).thenReturn(Mono.just(true));
        when(tokenRepository.existsByTenantIdAndPayloadAndStatus(TENANT, "p2", HashStatus.ACTIVE)).thenReturn(Mono.empty());

        StepVerifier.create(tokenAdapter.existsActiveByTenantAndPayload(TENANT, "p1")).expectNext(true).verifyComplete();
        StepVerifier.create(tokenAdapter.existsActiveByTenantAndPayload(TENANT, "p2")).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("token adapter: every listing variant delegates to its matching query")
    void tokenListings() {
        HashTokenEntity entity = HashTokenEntity.fromDomain(token);
        when(tokenRepository.findByTenantId(eq(TENANT), any(Pageable.class))).thenReturn(Flux.just(entity));
        when(tokenRepository.findByTenantIdAndStatus(eq(TENANT), eq(HashStatus.ACTIVE), any(Pageable.class))).thenReturn(Flux.just(entity));
        when(tokenRepository.findByTenantIdAndSourceService(eq(TENANT), eq("svc"), any(Pageable.class))).thenReturn(Flux.just(entity));
        when(tokenRepository.findByTenantIdAndSourceServiceAndStatus(eq(TENANT), eq("svc"), eq(HashStatus.ACTIVE), any(Pageable.class))).thenReturn(Flux.just(entity));

        StepVerifier.create(tokenAdapter.findAllByTenantId(TENANT, page)).expectNext(token).verifyComplete();
        StepVerifier.create(tokenAdapter.findAllByTenantIdAndStatus(TENANT, HashStatus.ACTIVE, page)).expectNext(token).verifyComplete();
        StepVerifier.create(tokenAdapter.findAllByTenantIdAndSourceService(TENANT, "svc", page)).expectNext(token).verifyComplete();
        StepVerifier.create(tokenAdapter.findAllByTenantIdAndSourceServiceAndStatus(TENANT, "svc", HashStatus.ACTIVE, page)).expectNext(token).verifyComplete();
    }

    // ------------------------------------------------------------ audit adapter

    @Test
    @DisplayName("audit adapter: constructor and every method reject null or blank arguments")
    void auditNullGuards() {
        assertThrows(NullPointerException.class, () -> new HashAuditRepositoryAdapter(null));
        assertThrows(NullPointerException.class, () -> auditAdapter.save(null));
        assertThrows(NullPointerException.class, () -> auditAdapter.findByTxId(null));
        assertThrows(NullPointerException.class, () -> auditAdapter.findByTenantId(null));
        assertThrows(IllegalArgumentException.class, () -> auditAdapter.findByTenantId(" "));
        assertThrows(NullPointerException.class, () -> auditAdapter.findByEntityId(null));
    }

    @Test
    @DisplayName("audit adapter: save maps through the entity; failures propagate")
    void auditSave() {
        when(auditRepository.save(any(HashAuditEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)))
                .thenReturn(Mono.error(new IllegalStateException("mongo down")));

        StepVerifier.create(auditAdapter.save(audit)).expectNext(audit).verifyComplete();
        StepVerifier.create(auditAdapter.save(audit)).expectErrorMessage("mongo down").verify();
    }

    @Test
    @DisplayName("audit adapter: lookups by transaction, tenant (newest first) and entity delegate and map")
    void auditLookups() {
        HashAuditEntity entity = HashAuditEntity.fromDomain(audit);
        when(auditRepository.findByTxId(audit.txId())).thenReturn(Flux.just(entity));
        when(auditRepository.findByTenantIdOrderByTimestampDesc(TENANT)).thenReturn(Flux.just(entity));
        when(auditRepository.findByEntityId(token.id())).thenReturn(Flux.just(entity));

        StepVerifier.create(auditAdapter.findByTxId(audit.txId())).expectNext(audit).verifyComplete();
        StepVerifier.create(auditAdapter.findByTenantId(TENANT)).expectNext(audit).verifyComplete();
        StepVerifier.create(auditAdapter.findByEntityId(token.id())).expectNext(audit).verifyComplete();

        verify(auditRepository).findByTenantIdOrderByTimestampDesc(TENANT);
    }
}
