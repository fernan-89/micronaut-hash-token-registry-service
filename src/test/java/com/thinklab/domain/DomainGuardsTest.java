package com.thinklab.domain;

import com.thinklab.application.usecase.command.DeactivateHashCommand;
import com.thinklab.application.usecase.command.GenerateHashCommand;
import com.thinklab.application.usecase.command.ListHashesQuery;
import com.thinklab.application.usecase.command.ReactivateHashCommand;
import com.thinklab.application.usecase.command.RevokeHashCommand;
import com.thinklab.application.usecase.command.SearchHashesQuery;
import com.thinklab.domain.exception.BusinessException;
import com.thinklab.domain.exception.InvalidHashStatusException;
import com.thinklab.domain.model.HashToken;
import com.thinklab.domain.valueobject.HashAlgorithm;
import com.thinklab.domain.valueobject.HashStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guard clauses and value-object behaviour that the lifecycle tests never reach. */
class DomainGuardsTest {

    private static final UUID ID = UUID.randomUUID();

    private static class TestException extends BusinessException {
        TestException(String code, String message) {
            super(code, message);
        }

        TestException(String code, String message, Throwable cause) {
            super(code, message, cause);
        }
    }

    // --- BusinessException -------------------------------------------------------------------------------

    @Test
    @DisplayName("BusinessException validates its code, message and cause")
    void businessExceptionGuards() {
        assertEquals("ERR-X", new TestException("ERR-X", "boom").getErrorCode());
        assertThrows(NullPointerException.class, () -> new TestException(null, "boom"));
        assertThrows(NullPointerException.class, () -> new TestException("ERR-X", null));
        assertThrows(IllegalArgumentException.class, () -> new TestException(" ", "boom"));
        assertThrows(IllegalArgumentException.class, () -> new TestException("ERR-X", " "));

        Throwable cause = new IllegalStateException("root");
        TestException withCause = new TestException("ERR-X", "boom", cause);
        assertSame(cause, withCause.getCause());
        assertEquals("ERR-X", withCause.getErrorCode());
        assertThrows(NullPointerException.class, () -> new TestException("ERR-X", "boom", null));
        assertThrows(IllegalArgumentException.class, () -> new TestException("", "boom", cause));
    }

    // --- HashAlgorithm -----------------------------------------------------------------------------------

    @Test
    @DisplayName("HashAlgorithm resolves standard names case-insensitively and rejects unknown ones")
    void algorithmLookup() {
        assertSame(HashAlgorithm.SHA3_512, HashAlgorithm.fromStandardName("sha3-512"));
        assertEquals("SHA3-512", HashAlgorithm.SHA3_512.getStandardName());
        assertThrows(NullPointerException.class, () -> HashAlgorithm.fromStandardName(null));
        assertThrows(IllegalArgumentException.class, () -> HashAlgorithm.fromStandardName("ROT13"));
    }

    @Test
    @DisplayName("HashAlgorithm exposes a message digest, and reports an unregistered provider algorithm")
    void algorithmDigest() {
        assertEquals("SHA-256", HashAlgorithm.SHA_256.getMessageDigest().getAlgorithm());
        assertThrows(IllegalStateException.class, () -> HashAlgorithm.digestFor("NOT-A-DIGEST"));
    }

    // --- HashStatus --------------------------------------------------------------------------------------

    @Test
    @DisplayName("HashStatus transition matrix")
    void statusMatrix() {
        assertTrue(HashStatus.ACTIVE.canTransitionTo(HashStatus.INACTIVE));
        assertTrue(HashStatus.ACTIVE.canTransitionTo(HashStatus.REVOKED));
        assertFalse(HashStatus.ACTIVE.canTransitionTo(HashStatus.ACTIVE));
        assertTrue(HashStatus.INACTIVE.canTransitionTo(HashStatus.ACTIVE));
        assertTrue(HashStatus.INACTIVE.canTransitionTo(HashStatus.REVOKED));
        assertFalse(HashStatus.INACTIVE.canTransitionTo(HashStatus.INACTIVE));
        for (HashStatus target : HashStatus.values()) {
            assertFalse(HashStatus.REVOKED.canTransitionTo(target));
            assertFalse(HashStatus.ACTIVE.canTransitionTo(null));
        }
        assertThrows(NullPointerException.class, () -> HashStatus.ACTIVE.validateTransitionTo(null));
        assertThrows(InvalidHashStatusException.class, () -> HashStatus.ACTIVE.validateTransitionTo(HashStatus.ACTIVE));
        assertThrows(InvalidHashStatusException.class, () -> HashStatus.REVOKED.validateTransitionTo(HashStatus.ACTIVE));
    }

    // --- HashToken ---------------------------------------------------------------------------------------

    private static HashToken token(String tenant, String source, String hash, String creator) {
        return new HashToken(ID, tenant, source, "p", "p", hash, HashAlgorithm.SHA_256, HashStatus.ACTIVE,
                creator, Instant.now(), null, null, 0L);
    }

    @Test
    @DisplayName("HashToken rejects null and blank mandatory fields")
    void tokenGuards() {
        assertThrows(IllegalArgumentException.class, () -> token(" ", "svc", "h", "me"));
        assertThrows(IllegalArgumentException.class, () -> token("t", " ", "h", "me"));
        assertThrows(IllegalArgumentException.class, () -> token("t", "svc", " ", "me"));
        assertThrows(IllegalArgumentException.class, () -> token("t", "svc", "h", " "));
        assertEquals(HashStatus.ACTIVE, token("t", "svc", "h", "me").status());
    }

    @Test
    @DisplayName("HashToken transitions reject a null or blank executor")
    void tokenExecutorGuards() {
        HashToken active = token("t", "svc", "h", "me");
        HashToken inactive = active.deactivate("op");

        assertThrows(NullPointerException.class, () -> active.deactivate(null));
        assertThrows(IllegalArgumentException.class, () -> active.deactivate(" "));
        assertThrows(NullPointerException.class, () -> inactive.reactivate(null));
        assertThrows(IllegalArgumentException.class, () -> inactive.reactivate(" "));
        assertThrows(NullPointerException.class, () -> active.revoke(null));
        assertThrows(IllegalArgumentException.class, () -> active.revoke(" "));
        assertEquals(HashStatus.REVOKED, active.revoke("op").status());
        assertEquals(HashStatus.ACTIVE, inactive.reactivate("op").status());
        assertNull(active.updatedBy());
    }

    // --- Commands ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("Deactivate, reactivate and revoke commands enforce executor and reason rules")
    void lifecycleCommandGuards() {
        assertThrows(IllegalArgumentException.class, () -> new DeactivateHashCommand(ID, "  ", "valid reason"));
        assertThrows(IllegalArgumentException.class, () -> new DeactivateHashCommand(ID, "op", " abc "));
        assertThrows(IllegalArgumentException.class, () -> new ReactivateHashCommand(ID, "  ", "valid reason"));
        assertThrows(IllegalArgumentException.class, () -> new ReactivateHashCommand(ID, "op", "abcd"));
        assertThrows(IllegalArgumentException.class, () -> new RevokeHashCommand(ID, " ", "a sufficiently long reason"));
        assertThrows(IllegalArgumentException.class, () -> new RevokeHashCommand(ID, "op", "too short"));
        assertEquals("valid reason", new DeactivateHashCommand(ID, "op", "  valid reason ").reason());
    }

    @Test
    @DisplayName("Generate command rejects blank tenant, payload, source or executor")
    void generateCommandGuards() {
        Function<String[], GenerateHashCommand> build = a ->
                new GenerateHashCommand(a[0], a[1], HashAlgorithm.SHA_256, a[2], a[3], false);

        assertThrows(IllegalArgumentException.class, () -> build.apply(new String[]{" ", "p", "s", "e"}));
        assertThrows(IllegalArgumentException.class, () -> build.apply(new String[]{"t", " ", "s", "e"}));
        assertThrows(IllegalArgumentException.class, () -> build.apply(new String[]{"t", "p", " ", "e"}));
        assertThrows(IllegalArgumentException.class, () -> build.apply(new String[]{"t", "p", "s", " "}));
        assertEquals("t", build.apply(new String[]{" t ", "p", "s", "e"}).tenantId());
    }

    @Test
    @DisplayName("List and search queries require a tenant and default the pagination window")
    void queryDefaults() {
        assertThrows(IllegalArgumentException.class, () -> new ListHashesQuery(" ", null, null, null));
        ListHashesQuery defaults = new ListHashesQuery("t", null, null, null);
        assertEquals(0, defaults.page());
        assertEquals(20, defaults.size());
        ListHashesQuery explicit = new ListHashesQuery("t", HashStatus.ACTIVE, 3, 50);
        assertEquals(3, explicit.page());
        assertEquals(50, explicit.size());

        assertThrows(IllegalArgumentException.class, () -> new SearchHashesQuery(" ", null, null, null, null));
        assertNull(new SearchHashesQuery("t", "  ", null, null, null).sourceService());
        assertEquals("svc", new SearchHashesQuery("t", " svc ", HashStatus.ACTIVE, 1, 5).sourceService());
        SearchHashesQuery defaulted = new SearchHashesQuery("t", null, null, null, null);
        assertEquals(0, defaulted.page());
        assertEquals(20, defaulted.size());
    }
}
