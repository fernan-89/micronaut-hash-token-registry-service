package com.thinklab.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 *  Domain Layer Unit Test: Forensic Audit Aggregate (HashAudit).
 *  This suite validates the integrity of the immutable forensic ledger, ensuring that
 *  all security-critical events are captured with strict adherence to identity sovereignty
 *  and defensive domain invariants.
 *
 *  <p><b>Architectural Principles:</b></p>
 *  <ul>
 *      <li><b>Identity Sovereignty:</b> Validates the usage of native {@link UUID} for primary and correlation keys.</li>
 *      <li><b>Deep Immutability:</b> Ensures that forensic records cannot be altered post-materialization.</li>
 *      <li><b>Fail-Fast Validation:</b> Confirms that the domain boundary rejects malformed or incomplete audit data.</li>
 *  </ul>
 *
 *  @version 2.0.0
 *  @see HashAudit
 */
class HashAuditTest {

    /**
     * Validates the primary lifecycle orchestration: successful instantiation via the
     * factory method with high-assurance UUID correlation.
     */
    @Test
    @DisplayName("Should successfully instantiate HashAudit using the standardized factory method")
    void shouldCreateHashAuditViaFactory() {
        // Given: Deterministic identity seeds and forensic metadata
        UUID txId = UUID.randomUUID();
        String tenantId = "TENANT-DEMO-CORE-01";
        UUID entityId = UUID.randomUUID();
        String operation = "GENERATE_HASH";
        String status = "SUCCESS";
        String executorId = "staff-engineer-01";
        Map<String, Object> metadata = Map.of("ip_address", "10.0.0.1", "tier", "MISSION_CRITICAL");

        // When: Materializing the forensic record
        HashAudit audit = HashAudit.create(txId, tenantId, entityId, operation, status, executorId, metadata);

        // Then: Assert immutable state integrity
        assertNotNull(audit.id(), "The system-generated Audit ID must be populated.");
        assertEquals(txId, audit.txId(), "The Transaction Correlation ID must match the reactive context.");
        assertNotNull(audit.timestamp(), "The forensic timestamp must be automatically materialized.");

        assertEquals(tenantId, audit.tenantId());
        assertEquals(entityId, audit.entityId());
        assertEquals(operation, audit.operation());
        assertEquals(status, audit.status());
        assertEquals(executorId, audit.executorId());

        // Assert deep equality of the context map
        assertEquals(metadata, audit.metadata());
    }

    /**
     * Validates defensive sanitization: ensuring that a null metadata input
     * results in a safe, empty immutable map instead of a NullPointerException.
     */
    @Test
    @DisplayName("Should handle null metadata by initializing an empty immutable context map")
    void shouldHandleNullMetadataInFactory() {
        // When: Attempting to create an audit with a null context payload
        HashAudit audit = HashAudit.create(
                UUID.randomUUID(), "TENANT-DEMO-01", UUID.randomUUID(),
                "DEACTIVATION", "SUCCESS", "system-agent", null
        );

        // Then: The aggregate must protect itself by providing a safe fallback
        assertNotNull(audit.metadata(), "Forensic metadata collection must never be null.");
        assertTrue(audit.metadata().isEmpty(), "Empty context should be represented as an empty map.");
    }

    /**
     * Validates the Immutability Invariant: once created, the audit entry
     * must block all modification attempts to preserve forensic irrefutability.
     */
    @Test
    @DisplayName("Should enforce strict immutability on the metadata context to preserve forensic integrity")
    void shouldEnsureMetadataIsImmutable() {
        // Given: A mutable map being used as an input seed
        Map<String, Object> mutableMetadata = new HashMap<>();
        mutableMetadata.put("initial_key", "initial_value");

        HashAudit audit = HashAudit.create(
                UUID.randomUUID(), "TENANT-DEMO-01", UUID.randomUUID(),
                "CREATION", "SUCCESS", "admin", mutableMetadata
        );

        // When & Then: Attempting to poison the audit entry post-creation
        Map<String, Object> auditMetadata = audit.metadata();

        assertThrows(UnsupportedOperationException.class, () -> {
            auditMetadata.put("unauthorized_key", "unauthorized_value");
        }, "The domain must reject state mutations on forensic metadata.");
    }

    /**
     * Validates Fail-Fast behavior: ensures that mandatory identity and
     * operational fields are strictly enforced during constructor invocation.
     */
    @Test
    @DisplayName("Should trigger Fail-Fast NullPointerException when mandatory identifiers are missing")
    void shouldThrowExceptionWhenRequiredFieldsAreNull() {
        UUID validId = UUID.randomUUID();
        Instant now = Instant.now();

        // 1. Enforce ID existence
        assertThrows(NullPointerException.class, () -> new HashAudit(
                null, validId, "TENANT-1", validId, "OP", "OK", "user", now, Map.of()
        ), "Audit identity identifier is mandatory.");

        // 2. Enforce Temporal integrity
        assertThrows(NullPointerException.class, () -> new HashAudit(
                validId, validId, "TENANT-1", validId, "OP", "OK", "user", null, Map.of()
        ), "Chronological event timestamp is mandatory.");

        // 3. Enforce Transactional correlation
        assertThrows(NullPointerException.class, () -> new HashAudit(
                validId, null, "TENANT-1", validId, "OP", "OK", "user", now, Map.of()
        ), "Reactive transaction correlation identifier is mandatory.");
    }

    /**
     * Validates Boundary Defense: ensures that blank strings are rejected
     * to prevent "Shadow Audits" (records with no identifiable context).
     */
    @Test
    @DisplayName("Should trigger IllegalArgumentException when mandatory string identifiers are blank")
    void shouldRejectBlankStringIdentifiers() {
        UUID validId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
                        HashAudit.create(validId, "   ", validId, "GENERATE", "OK", "user", null)
                , "Tenant ID cannot be blank in forensic trails.");

        assertThrows(IllegalArgumentException.class, () ->
                        HashAudit.create(validId, "TENANT-1", validId, "", "OK", "user", null)
                , "Operation name is required for compliance audit.");
    }
}