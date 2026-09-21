package com.thinklab.infrastructure.adapter;

import com.thinklab.application.usecase.command.ReactivateHashCommand;
import com.thinklab.domain.model.HashAudit;
import com.thinklab.domain.model.HashToken;
import com.thinklab.domain.valueobject.HashAlgorithm;
import com.thinklab.domain.valueobject.HashStatus;
import com.thinklab.infrastructure.adapter.in.web.dto.request.ReactivateHashRequest;
import com.thinklab.infrastructure.adapter.in.web.dto.response.DeactivateHashResponse;
import com.thinklab.infrastructure.adapter.in.web.dto.response.HashAuditResponse;
import com.thinklab.infrastructure.adapter.in.web.dto.response.HashFullResponse;
import com.thinklab.infrastructure.adapter.in.web.dto.response.HashResponse;
import com.thinklab.infrastructure.adapter.in.web.dto.response.PagedHashResponse;
import com.thinklab.infrastructure.adapter.out.mongo.entity.HashAuditEntity;
import com.thinklab.infrastructure.adapter.out.mongo.entity.HashTokenEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Invariants of the persistence entities, response projections and the audit aggregate. */
class ProjectionInvariantsTest {

    private static final UUID ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    // --- HashAudit / HashAuditEntity / HashAuditResponse ---------------------------------------------------

    @Test
    @DisplayName("HashAudit rejects blank tenant, operation, status and executor")
    void auditAggregateGuards() {
        assertThrows(IllegalArgumentException.class, () -> new HashAudit(ID, ID, " ", ID, "op", "OK", "me", NOW, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new HashAudit(ID, ID, "t", ID, " ", "OK", "me", NOW, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new HashAudit(ID, ID, "t", ID, "op", " ", "me", NOW, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new HashAudit(ID, ID, "t", ID, "op", "OK", " ", NOW, Map.of()));
    }

    @Test
    @DisplayName("HashAuditEntity guards, null-metadata defaulting and domain round trip")
    void auditEntity() {
        assertThrows(NullPointerException.class, () -> new HashAuditEntity(null, ID, "t", ID, "op", "OK", "me", NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditEntity(ID, null, "t", ID, "op", "OK", "me", NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditEntity(ID, ID, null, ID, "op", "OK", "me", NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditEntity(ID, ID, "t", null, "op", "OK", "me", NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditEntity(ID, ID, "t", ID, null, "OK", "me", NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditEntity(ID, ID, "t", ID, "op", null, "me", NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditEntity(ID, ID, "t", ID, "op", "OK", null, NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditEntity(ID, ID, "t", ID, "op", "OK", "me", null, null));
        assertThrows(IllegalArgumentException.class, () -> new HashAuditEntity(ID, ID, " ", ID, "op", "OK", "me", NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new HashAuditEntity(ID, ID, "t", ID, " ", "OK", "me", NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new HashAuditEntity(ID, ID, "t", ID, "op", " ", "me", NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new HashAuditEntity(ID, ID, "t", ID, "op", "OK", " ", NOW, null));

        assertTrue(new HashAuditEntity(ID, ID, "t", ID, "op", "OK", "me", NOW, null).metadata().isEmpty());
        HashAudit domain = HashAudit.create(ID, "t", ID, "op", "OK", "me", Map.of("k", "v"));
        HashAudit roundTrip = HashAuditEntity.fromDomain(domain).toDomain();
        assertEquals(domain, roundTrip);
        assertThrows(NullPointerException.class, () -> HashAuditEntity.fromDomain(null));
    }

    @Test
    @DisplayName("HashAuditResponse guards, metadata defaulting and projection")
    void auditResponse() {
        assertThrows(NullPointerException.class, () -> new HashAuditResponse(null, ID, "t", "op", "OK", "me", NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditResponse(ID, null, "t", "op", "OK", "me", NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditResponse(ID, ID, null, "op", "OK", "me", NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditResponse(ID, ID, "t", null, "OK", "me", NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditResponse(ID, ID, "t", "op", null, "me", NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditResponse(ID, ID, "t", "op", "OK", null, NOW, null));
        assertThrows(NullPointerException.class, () -> new HashAuditResponse(ID, ID, "t", "op", "OK", "me", null, null));
        assertThrows(IllegalArgumentException.class, () -> new HashAuditResponse(ID, ID, " ", "op", "OK", "me", NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new HashAuditResponse(ID, ID, "t", " ", "OK", "me", NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new HashAuditResponse(ID, ID, "t", "op", " ", "me", NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new HashAuditResponse(ID, ID, "t", "op", "OK", " ", NOW, null));

        assertTrue(new HashAuditResponse(ID, ID, "t", "op", "OK", "me", NOW, null).metadata().isEmpty());
        HashAudit domain = HashAudit.create(ID, "t", ID, "op", "OK", "me", Map.of("k", "v"));
        assertEquals("v", HashAuditResponse.fromDomain(domain).metadata().get("k"));
        assertThrows(NullPointerException.class, () -> HashAuditResponse.fromDomain(null));
    }

    // --- HashToken entity / responses ----------------------------------------------------------------------

    private static HashTokenEntity entity(UUID id, String tenant, String source, String payload, String original, String hash,
                                          HashAlgorithm algorithm, HashStatus status, String creator, Instant created, Long version) {
        return new HashTokenEntity(id, tenant, source, payload, original, hash, algorithm, status, creator, created, null, null, version);
    }

    @Test
    @DisplayName("HashTokenEntity rejects null and blank mandatory fields")
    void tokenEntityGuards() {
        HashAlgorithm a = HashAlgorithm.SHA_256;
        HashStatus s = HashStatus.ACTIVE;
        assertThrows(NullPointerException.class, () -> entity(null, "t", "s", "p", "p", "h", a, s, "me", NOW, 0L));
        assertThrows(NullPointerException.class, () -> entity(ID, null, "s", "p", "p", "h", a, s, "me", NOW, 0L));
        assertThrows(NullPointerException.class, () -> entity(ID, "t", null, "p", "p", "h", a, s, "me", NOW, 0L));
        assertThrows(NullPointerException.class, () -> entity(ID, "t", "s", null, "p", "h", a, s, "me", NOW, 0L));
        assertThrows(NullPointerException.class, () -> entity(ID, "t", "s", "p", null, "h", a, s, "me", NOW, 0L));
        assertThrows(NullPointerException.class, () -> entity(ID, "t", "s", "p", "p", null, a, s, "me", NOW, 0L));
        assertThrows(NullPointerException.class, () -> entity(ID, "t", "s", "p", "p", "h", null, s, "me", NOW, 0L));
        assertThrows(NullPointerException.class, () -> entity(ID, "t", "s", "p", "p", "h", a, null, "me", NOW, 0L));
        assertThrows(NullPointerException.class, () -> entity(ID, "t", "s", "p", "p", "h", a, s, null, NOW, 0L));
        assertThrows(NullPointerException.class, () -> entity(ID, "t", "s", "p", "p", "h", a, s, "me", null, 0L));
        assertThrows(IllegalArgumentException.class, () -> entity(ID, " ", "s", "p", "p", "h", a, s, "me", NOW, 0L));
        assertThrows(IllegalArgumentException.class, () -> entity(ID, "t", " ", "p", "p", "h", a, s, "me", NOW, 0L));
        assertThrows(IllegalArgumentException.class, () -> entity(ID, "t", "s", " ", "p", "h", a, s, "me", NOW, 0L));
        assertThrows(IllegalArgumentException.class, () -> entity(ID, "t", "s", "p", "p", " ", a, s, "me", NOW, 0L));
        assertThrows(IllegalArgumentException.class, () -> entity(ID, "t", "s", "p", "p", "h", a, s, " ", NOW, 0L));
    }

    @Test
    @DisplayName("HashTokenEntity maps to the domain, defaulting a missing version to zero")
    void tokenEntityMapping() {
        HashTokenEntity noVersion = entity(ID, "t", "s", "p", "p", "h", HashAlgorithm.SHA_256, HashStatus.ACTIVE, "me", NOW, null);
        assertEquals(0L, noVersion.toDomain().version());

        HashToken domain = HashToken.create(ID, "t", "s", "p", "p", "h", HashAlgorithm.SHA_256, "me");
        assertEquals(domain, HashTokenEntity.fromDomain(domain).toDomain());
        assertThrows(NullPointerException.class, () -> HashTokenEntity.fromDomain(null));
    }

    @Test
    @DisplayName("HashResponse rejects null and blank mandatory fields and projects a domain token")
    void hashResponse() {
        HashAlgorithm a = HashAlgorithm.SHA_256;
        HashStatus s = HashStatus.ACTIVE;
        assertThrows(NullPointerException.class, () -> new HashResponse(null, "t", "s", "h", a, s, NOW, null, 0L));
        assertThrows(NullPointerException.class, () -> new HashResponse(ID, null, "s", "h", a, s, NOW, null, 0L));
        assertThrows(NullPointerException.class, () -> new HashResponse(ID, "t", null, "h", a, s, NOW, null, 0L));
        assertThrows(NullPointerException.class, () -> new HashResponse(ID, "t", "s", null, a, s, NOW, null, 0L));
        assertThrows(NullPointerException.class, () -> new HashResponse(ID, "t", "s", "h", null, s, NOW, null, 0L));
        assertThrows(NullPointerException.class, () -> new HashResponse(ID, "t", "s", "h", a, null, NOW, null, 0L));
        assertThrows(NullPointerException.class, () -> new HashResponse(ID, "t", "s", "h", a, s, null, null, 0L));
        assertThrows(NullPointerException.class, () -> new HashResponse(ID, "t", "s", "h", a, s, NOW, null, null));
        assertThrows(IllegalArgumentException.class, () -> new HashResponse(ID, " ", "s", "h", a, s, NOW, null, 0L));
        assertThrows(IllegalArgumentException.class, () -> new HashResponse(ID, "t", " ", "h", a, s, NOW, null, 0L));
        assertThrows(IllegalArgumentException.class, () -> new HashResponse(ID, "t", "s", " ", a, s, NOW, null, 0L));
        assertThrows(NullPointerException.class, () -> HashResponse.fromDomain(null));
    }

    @Test
    @DisplayName("PagedHashResponse enforces its pagination bounds and immutability")
    void pagedResponse() {
        assertThrows(NullPointerException.class, () -> new PagedHashResponse(null, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new PagedHashResponse(List.of(), 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PagedHashResponse(List.of(), 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PagedHashResponse(List.of(), -1, 0, 1));

        HashResponse one = HashResponse.fromDomain(HashToken.create(ID, "t", "s", "p", "p", "h", HashAlgorithm.SHA_256, "me"));
        PagedHashResponse page = PagedHashResponse.of(List.of(one), 1, 0, 20);
        assertEquals(1, page.content().size());
        assertThrows(UnsupportedOperationException.class, () -> page.content().add(one));
        assertTrue(PagedHashResponse.of(List.of(), 0, 0, 20).content().isEmpty());
    }

    @Test
    @DisplayName("DeactivateHashResponse rejects blank fields and falls back to now when the token has no update time")
    void deactivateResponse() {
        assertThrows(NullPointerException.class, () -> new DeactivateHashResponse(null, "s", "e", "r", NOW));
        assertThrows(NullPointerException.class, () -> new DeactivateHashResponse(ID, null, "e", "r", NOW));
        assertThrows(NullPointerException.class, () -> new DeactivateHashResponse(ID, "s", null, "r", NOW));
        assertThrows(NullPointerException.class, () -> new DeactivateHashResponse(ID, "s", "e", null, NOW));
        assertThrows(NullPointerException.class, () -> new DeactivateHashResponse(ID, "s", "e", "r", null));
        assertThrows(IllegalArgumentException.class, () -> new DeactivateHashResponse(ID, " ", "e", "r", NOW));
        assertThrows(IllegalArgumentException.class, () -> new DeactivateHashResponse(ID, "s", " ", "r", NOW));
        assertThrows(IllegalArgumentException.class, () -> new DeactivateHashResponse(ID, "s", "e", " ", NOW));

        HashToken fresh = HashToken.create(ID, "t", "s", "p", "p", "h", HashAlgorithm.SHA_256, "me");
        assertTrue(DeactivateHashResponse.fromDomain(fresh, "op", "why").deactivatedAt() != null);
        HashToken updated = fresh.deactivate("op");
        assertEquals(updated.updatedAt(), DeactivateHashResponse.fromDomain(updated, "op", "why").deactivatedAt());
        assertThrows(NullPointerException.class, () -> DeactivateHashResponse.fromDomain(null, "op", "why"));
    }

    @Test
    @DisplayName("HashFullResponse requires the core hash and defaults the audit trail to empty")
    void fullResponse() {
        HashResponse core = HashResponse.fromDomain(HashToken.create(ID, "t", "s", "p", "p", "h", HashAlgorithm.SHA_256, "me"));
        assertThrows(NullPointerException.class, () -> HashFullResponse.of(null, List.of()));
        assertTrue(HashFullResponse.of(core, null).auditLogs().isEmpty());
        assertTrue(HashFullResponse.of(core, List.of()).auditLogs().isEmpty());
        HashAuditResponse audit = new HashAuditResponse(ID, ID, "t", "op", "OK", "me", NOW, null);
        assertEquals(1, HashFullResponse.of(core, List.of(audit)).auditLogs().size());
        assertSame(core, HashFullResponse.of(core, null).hash());
    }

    @Test
    @DisplayName("ReactivateHashRequest translates to a command and rejects a null target id")
    void reactivateRequest() {
        ReactivateHashRequest request = new ReactivateHashRequest("valid reason");

        ReactivateHashCommand command = request.toCommand(ID, "op");

        assertEquals(ID, command.hashId());
        assertEquals("valid reason", command.reason());
        assertThrows(NullPointerException.class, () -> request.toCommand(null, "op"));
    }
}
