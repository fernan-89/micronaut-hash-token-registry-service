package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidHashStatusException;
import com.thinklab.domain.valueobject.HashAlgorithm;
import com.thinklab.domain.valueobject.HashStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  Domain Layer Unit Test: Aggregate Root (HashToken).
 *  This suite validates the high-assurance lifecycle of cryptographic tokens,
 *  ensuring that state transitions strictly adhere to the Finite State Machine (FSM)
 *  and that all mutations preserve absolute functional immutability.
 *
 *  <p><b>Architectural Principles:</b></p>
 *  <ul>
 *      <li><b>Identity Sovereignty:</b> Validates usage of native {@link UUID} for primary identifiers.</li>
 *      <li><b>Functional Immutability:</b> Verifies that state mutations yield new instances (side-effect free).</li>
 *      <li><b>State Machine Integrity:</b> Enforces valid/invalid transition paths as per domain invariants.</li>
 *  </ul>
 *
 *  @version 2.0.0
 *  @see HashToken
 */
class HashTokenTest {

    /**
     * Validates the primary factory orchestration: successful instantiation
     * with an initial ACTIVE status and zero-version control.
     */
    @Test
    @DisplayName("Should successfully instantiate a HashToken via factory with ACTIVE initial state")
    void shouldCreateHashTokenSuccessfully() {
        // Given: High-assurance identity seeds and cryptographic metadata
        UUID id = UUID.randomUUID();
        String tenantId = "TENANT-DEMO-CORE-01";
        String sourceService = "mission-control-api";
        String payload = "SEEDED-DATA-2026";
        String generatedHash = "3f2e1a...f8e9";
        HashAlgorithm algorithm = HashAlgorithm.SHA3_512;
        String creator = "staff-engineer-01";

        // When: Materializing the new aggregate root
        HashToken token = HashToken.create(
                id, tenantId, sourceService, payload, payload, generatedHash, algorithm, creator
        );

        // Then: Assert structural and behavioral integrity
        assertNotNull(token, "The aggregate must be materialized.");
        assertEquals(id, token.id(), "Identity sovereignty check: ID must match input UUID.");
        assertEquals(tenantId, token.tenantId());
        assertEquals(HashStatus.ACTIVE, token.status(), "New tokens must always materialize in ACTIVE state.");
        assertEquals(creator, token.createdBy());
        assertEquals(0L, token.version(), "Initial optimistic locking version must be 0.");
        assertNotNull(token.createdAt(), "Temporal creation anchor must be generated.");

        // Ensure audit fields for updates are properly uninitialized
        assertNull(token.updatedBy(), "New tokens must not have an update actor.");
        assertNull(token.updatedAt(), "New tokens must not have an update timestamp.");
    }

    /**
     * Validates the transition to INACTIVE status.
     * Ensures the mutation is side-effect free and captures forensic metadata.
     */
    @Test
    @DisplayName("Should transition from ACTIVE to INACTIVE state immutably")
    void shouldDeactivateTokenImmutably() {
        // Given: A valid active aggregate
        HashToken token = createActiveToken();
        String executor = "security-auditor-01";

        // When: Executing status suspension
        HashToken deactivatedToken = token.deactivate(executor);

        // Then: Assert functional purity and state correctness
        assertNotSame(token, deactivatedToken, "Functional mutation check: Must return a NEW immutable instance.");
        assertEquals(HashStatus.INACTIVE, deactivatedToken.status());
        assertEquals(executor, deactivatedToken.updatedBy());
        assertNotNull(deactivatedToken.updatedAt());
        assertEquals(token.version(), deactivatedToken.version(), "Internal state machine version remains constant until repository commit.");
    }

    /**
     * Validates the restoration workflow: restoring an INACTIVE token back to ACTIVE.
     */
    @Test
    @DisplayName("Should restore an INACTIVE token back to ACTIVE operational status")
    void shouldReactivateTokenSuccessfully() {
        // Given: A token previously transitioned to INACTIVE
        HashToken inactiveToken = createActiveToken().deactivate("system-agent");
        String restorer = "recovery-officer-99";

        // When: Restoring operational capability
        HashToken restoredToken = inactiveToken.reactivate(restorer);

        // Then: Assert successful restoration
        assertEquals(HashStatus.ACTIVE, restoredToken.status());
        assertEquals(restorer, restoredToken.updatedBy());
    }

    /**
     * Validates the irreversible terminal transition to REVOKED.
     * Based on Zero Trust principles, revoked tokens cannot be reactivated.
     */
    @Test
    @DisplayName("Should transition to terminal REVOKED state irreversibly")
    void shouldRevokeTokenPermanently() {
        // Given: An active operational token
        HashToken token = createActiveToken();
        String revoker = "secops-admin";

        // When: Executing terminal revocation
        HashToken revokedToken = token.revoke(revoker);

        // Then: State must be terminal
        assertEquals(HashStatus.REVOKED, revokedToken.status());

        // Verify state machine enforcement: REVOKED -> ACTIVE must fail
        assertThrows(InvalidHashStatusException.class, () ->
                        revokedToken.reactivate("unauthorized-recovery-attempt"),
                "Zero Trust Violation: Domain must block any transition out of terminal REVOKED state."
        );
    }

    /**
     * Validates self-transition prevention.
     * Prevents redundant operations and audit noise.
     */
    @Test
    @DisplayName("Should reject redundant state transitions to the current status")
    void shouldRejectSelfTransition() {
        HashToken token = createActiveToken();

        assertThrows(InvalidHashStatusException.class, () ->
                token.reactivate("system"), "Domain must reject redundant activation."
        );
    }

    // --- Helper Methods ---

    /**
     * Creates a standardized ACTIVE token seed for lifecycle tests.
     */
    private HashToken createActiveToken() {
        return HashToken.create(
                UUID.randomUUID(), "TENANT-DEMO-SEED", "test-service",
                "payload", "payload", "hash-v1", HashAlgorithm.SHA_256, "unit-test-runner"
        );
    }
}