package com.thinklab.infrastructure.adapter.out.mongo;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.application.port.out.HashAuditRepositoryPort;
import com.thinklab.application.port.out.HashTokenRepositoryPort;
import com.thinklab.domain.valueobject.HashAlgorithm;
import com.thinklab.domain.model.HashAudit;
import com.thinklab.domain.valueobject.HashStatus;
import com.thinklab.domain.model.HashToken;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The persistence adapters and their Micronaut Data repositories against a real MongoDB: the generated
 * derived queries, UUID ids, {@code @Version} optimistic locking, paging, counts and declared indexes —
 * everything the unit suite can only mock.
 */
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HashPersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "thinklab_hash_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject
    HashTokenRepositoryPort tokens;

    @Inject
    HashAuditRepositoryPort audits;

    @Inject
    MongoClient mongoClient;

    /** Mongo stores milliseconds; build fixtures at that precision so round trips compare equal. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String tenant() {
        return "TENANT-" + UUID.randomUUID();
    }

    private static HashToken token(String tenantId, String sourceService, String payload) {
        return new HashToken(UUID.randomUUID(), tenantId, sourceService, payload, payload + "-original",
                "HASH-" + UUID.randomUUID(), HashAlgorithm.SHA3_512, HashStatus.ACTIVE,
                "it-operator", now(), null, null, 0L);
    }

    private Set<String> indexedFields(String collection) {
        return Flux.from(mongoClient.getDatabase(DATABASE).getCollection(collection).listIndexes())
                .map(index -> ((Document) index.get("key")).keySet().stream().collect(Collectors.joining("+")))
                .collect(Collectors.toSet())
                .block();
    }

    @Test
    @DisplayName("a saved token is read back unchanged by id")
    void tokenRoundTrip() {
        HashToken original = token(tenant(), "svc-a", "payload-" + UUID.randomUUID());

        HashToken saved = tokens.save(original).block();
        HashToken found = tokens.findById(original.id()).block();

        assertEquals(saved, found);
        assertEquals(original.generatedHash(), found.generatedHash());
        assertEquals(original.createdAt(), found.createdAt());
        assertEquals(HashStatus.ACTIVE, found.status());
    }

    @Test
    @DisplayName("an update bumps the version and a stale update is rejected (optimistic locking)")
    void optimisticLocking() {
        HashToken saved = tokens.save(token(tenant(), "svc-a", "payload-" + UUID.randomUUID())).block();

        HashToken updated = tokens.update(saved.deactivate("it-operator")).block();

        assertEquals(saved.version() + 1, updated.version());
        assertEquals(HashStatus.INACTIVE, tokens.findById(saved.id()).block().status());
        // `saved` still carries the old version: a concurrent writer that read before the update.
        assertThrows(OptimisticLockException.class, () -> tokens.update(saved.deactivate("stale-writer")).block());
    }

    @Test
    @DisplayName("the active-duplicate check matches only an ACTIVE token of the same tenant and payload")
    void existsActiveByTenantAndPayload() {
        String tenant = tenant();
        String payload = "payload-" + UUID.randomUUID();
        HashToken saved = tokens.save(token(tenant, "svc-a", payload)).block();

        assertTrue(tokens.existsActiveByTenantAndPayload(tenant, payload).block());
        assertFalse(tokens.existsActiveByTenantAndPayload(tenant(), payload).block());

        tokens.update(saved.deactivate("it-operator")).block();

        assertFalse(tokens.existsActiveByTenantAndPayload(tenant, payload).block());
    }

    @Test
    @DisplayName("listing is tenant-scoped and paged, and counts honour every filter combination")
    void pagingAndCounts() {
        String tenant = tenant();
        tokens.save(token(tenant, "svc-a", "p1")).block();
        tokens.save(token(tenant, "svc-a", "p2")).block();
        HashToken inactive = tokens.save(token(tenant, "svc-b", "p3")).block();
        tokens.update(inactive.deactivate("it-operator")).block();
        tokens.save(token(tenant(), "svc-a", "other-tenant")).block();

        assertEquals(2, tokens.findAllByTenantId(tenant, Pageable.from(0, 2)).collectList().block().size());
        assertEquals(1, tokens.findAllByTenantId(tenant, Pageable.from(1, 2)).collectList().block().size());
        assertEquals(2, tokens.findAllByTenantIdAndStatus(tenant, HashStatus.ACTIVE, Pageable.from(0, 10)).collectList().block().size());
        assertEquals(1, tokens.findAllByTenantIdAndSourceService(tenant, "svc-b", Pageable.from(0, 10)).collectList().block().size());
        assertEquals(1, tokens.findAllByTenantIdAndSourceServiceAndStatus(tenant, "svc-b", HashStatus.INACTIVE, Pageable.from(0, 10)).collectList().block().size());

        assertEquals(3L, tokens.countByFilters(tenant, null, null).block());
        assertEquals(2L, tokens.countByFilters(tenant, "svc-a", null).block());
        assertEquals(1L, tokens.countByFilters(tenant, null, HashStatus.INACTIVE).block());
        assertEquals(0L, tokens.countByFilters(tenant, "svc-a", HashStatus.INACTIVE).block());
    }

    @Test
    @DisplayName("audit records round-trip their metadata and are queryable by transaction, entity and tenant (newest first)")
    void auditLedger() {
        String tenant = tenant();
        UUID txId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        Instant base = now();
        HashAudit first = new HashAudit(UUID.randomUUID(), txId, tenant, entityId, "GENERATE", "SUCCESS", "it-operator",
                base, Map.of("ip_address", "10.0.0.1", "attempt", 1));
        HashAudit second = new HashAudit(UUID.randomUUID(), UUID.randomUUID(), tenant, entityId, "DEACTIVATE", "SUCCESS",
                "it-operator", base.plusSeconds(5), Map.of("reason", "rotation"));
        audits.save(first).block();
        audits.save(second).block();

        assertEquals(List.of(first), audits.findByTxId(txId).collectList().block());
        assertEquals(Set.of(first.id(), second.id()),
                audits.findByEntityId(entityId).map(HashAudit::id).collect(Collectors.toSet()).block());
        assertEquals(List.of(second.id(), first.id()),
                audits.findByTenantId(tenant).map(HashAudit::id).collectList().block());
    }

    @Test
    @DisplayName("the indexes declared on the entities exist in MongoDB")
    void declaredIndexesExist() {
        assertTrue(indexedFields("hash_token").containsAll(Set.of("tenantId+status", "tenantId+payload", "tenantId+generatedHash")),
                () -> "hash_token indexes: " + indexedFields("hash_token"));
        assertTrue(indexedFields("hash_audit").containsAll(Set.of("txId", "tenantId", "entityId", "executor", "timestamp")),
                () -> "hash_audit indexes: " + indexedFields("hash_audit"));
    }
}
