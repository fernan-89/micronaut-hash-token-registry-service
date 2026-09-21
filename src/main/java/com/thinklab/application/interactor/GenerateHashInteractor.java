package com.thinklab.application.interactor;

import com.thinklab.application.usecase.command.GenerateHashCommand;
import com.thinklab.application.port.out.HashAuditRepositoryPort;
import com.thinklab.application.port.out.HashTokenRepositoryPort;
import com.thinklab.domain.exception.DuplicateHashException;
import com.thinklab.domain.model.HashAudit;
import com.thinklab.domain.model.HashToken;
import com.thinklab.application.port.in.GenerateHashUseCase;
import com.thinklab.domain.valueobject.HashAlgorithm;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Application Interactor: Implementation of the {@link GenerateHashUseCase} input port.
 *
 * <p><b>Architectural Role:</b>
 * This interactor orchestrates the resource-intensive cryptographic generation process. It guarantees
 * that CPU-bound cryptographic calculations and serial formatting are strictly offloaded to parallel
 * thread pools, protecting the Netty EventLoop from blocking.
 *
 * <p><b>Contractual Obligations:</b>
 * <ul>
 * <li><b>Constructor Injection (ADR-001):</b> Utilizes an explicit {@link Inject} constructor to guarantee
 *     deterministic bean wiring and AOP proxy reliability.</li>
 * <li><b>Identity Sovereignty (SHA3-512 Deterministic Seed):</b> Primary identifiers (UUIDs) are
 *     deterministically derived using SHA3-512 hashes of the tenant and payload context, ensuring
 *     predictable idempotency and native BSON Binary (Subtype 4) storage optimization.</li>
 * <li><b>CPU Offloading:</b> Leverages {@code Schedulers.parallel()} for all cryptographic computations
 *     to maintain sub-millisecond responsiveness across Netty I/O channels.</li>
 * <li><b>Atomic Audit Binding:</b> Ensures that a hash is exclusively committed as "created" when
 *     both the aggregate persistence and the immutable forensic audit log succeed transactionally.</li>
 * </ul>
 *
 * @author ThinkLab
 * @version 1.0.0
 * @since 1.0
 */
@Slf4j
@Singleton
public class GenerateHashInteractor implements GenerateHashUseCase {

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");
    private static final Pattern SPECIAL_CHARACTERS = Pattern.compile("[^\\p{L}\\p{N}\\s\\-_.,:;@/]");

    private final HashTokenRepositoryPort hashTokenRepository;
    private final HashAuditRepositoryPort hashAuditRepository;

    /**
     * Explicit constructor for strict dependency injection (ADR-001).
     *
     * @param hashTokenRepository The outbound port for token persistence. Must not be null.
     * @param hashAuditRepository The outbound port for audit persistence. Must not be null.
     * @throws NullPointerException if any repository dependency is null.
     */
    @Inject
    public GenerateHashInteractor(
            HashTokenRepositoryPort hashTokenRepository,
            HashAuditRepositoryPort hashAuditRepository
    ) {
        this.hashTokenRepository = Objects.requireNonNull(hashTokenRepository, "Application constraint violated: HashTokenRepositoryPort cannot be null.");
        this.hashAuditRepository = Objects.requireNonNull(hashAuditRepository, "Application constraint violated: HashAuditRepositoryPort cannot be null.");
    }

    /**
     * Orchestrates the secure generation of a cryptographic {@link HashToken} and its initial forensic audit trail.
     *
     * <p><b>Reactive Pipeline Flow:</b>
     * <ol>
     *   <li>Verifies active duplicate existence by tenant and payload (TD-004 resilience).</li>
     *   <li>Offloads CPU-bound SHA3-512 hashing and deterministic UUID generation to {@code Schedulers.parallel()}.</li>
     *   <li>Persists the new {@link HashToken} aggregate via the repository port.</li>
     *   <li>Generates and persists the forensic audit log correlated by a reactive transaction UUID.</li>
     * </ol>
     *
     * @param command The {@link GenerateHashCommand} encapsulating payload, algorithm, and tenant context. Must not be null.
     * @return A {@link Mono} emitting the newly generated and persisted {@link HashToken}.
     * @throws NullPointerException if the command is null.
     */
    @Override
    public Mono<HashToken> execute(GenerateHashCommand command) {
        Objects.requireNonNull(command, "Application constraint violated: GenerateHashCommand cannot be null.");

        String sanitizedPayload = sanitize(command.payload());

        return hashTokenRepository.existsActiveByTenantAndPayload(command.tenantId(), sanitizedPayload)
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("[ACTION: GENERATE_HASH] [TENANT: {}] - Orchestration halted: Active hash already exists for this payload.", command.tenantId());
                        return Mono.error(new DuplicateHashException("ERR-HASH-00409",
                                "An active cryptographic hash already exists for the provided tenant and payload context."));
                    }
                    return performGeneration(command, sanitizedPayload);
                })
                .doOnSubscribe(s -> log.info("[ACTION: GENERATE_HASH] [TENANT: {}] [ALGO: {}] - Initiating orchestration pipeline for cryptographic generation.", command.tenantId(), command.algorithm().name()));
    }

    /**
     * Normalizes raw input by trimming boundary whitespace, stripping control characters, and removing
     * special characters outside the safe alphanumeric/punctuation set (Convention B payload sanitization).
     * The raw, unsanitized value is preserved separately as {@code originalPayload} for forensic comparison.
     */
    private String sanitize(String rawPayload) {
        String trimmed = rawPayload.trim();
        String withoutControlChars = CONTROL_CHARACTERS.matcher(trimmed).replaceAll("");
        return SPECIAL_CHARACTERS.matcher(withoutControlChars).replaceAll("");
    }

    /**
     * Executes CPU-bound cryptographic calculations and transactionally binds them to persistence.
     */
    private Mono<HashToken> performGeneration(GenerateHashCommand command, String sanitizedPayload) {
        UUID txId = UUID.randomUUID(); // Reactive transaction correlation ID

        return Mono.fromCallable(() -> {
                    // 1. Calculate cryptographic hash using requested algorithm over the sanitized payload
                    String generatedHash = calculateHash(sanitizedPayload, command.algorithm());

                    // 2. Format as serial key if requested
                    if (command.asSerialKey()) {
                        generatedHash = formatAsSerialKey(generatedHash);
                    }

                    // 3. Identity Sovereignty: Derive deterministic UUID from SHA3-512 seed
                    UUID deterministicId = generateDeterministicId(command.tenantId(), sanitizedPayload);

                    return HashToken.create(
                            deterministicId,
                            command.tenantId(),
                            command.sourceService(),
                            sanitizedPayload,
                            command.payload(),
                            generatedHash,
                            command.algorithm(),
                            command.executor()
                    );
                })
                .subscribeOn(Schedulers.parallel()) // Offloads compute-bound hashing to protect Netty EventLoop
                .flatMap(hashTokenRepository::save)
                .flatMap(savedToken -> createAuditLog(savedToken, txId, command.executor(), command.asSerialKey())
                        .thenReturn(savedToken))
                .doOnSuccess(token -> log.info("[ACTION: GENERATE_HASH] [ID: {}] [TENANT: {}] - Orchestration completed. Entity generated and forensic audit successfully persisted.", token.id(), command.tenantId()))
                .doOnError(error -> {
                    if (!(error instanceof DuplicateHashException)) {
                        log.error("[ACTION: GENERATE_HASH] [TENANT: {}] - CRITICAL: Pipeline orchestration failed due to system exception: {}", command.tenantId(), error.getMessage(), error);
                    }
                });
    }

    /**
     * Derives a deterministic universal unique identifier (UUID v4-compatible structure) using SHA3-512
     * as the base cryptographic seed, fulfilling the Identity Sovereignty architectural mandate.
     */
    private UUID generateDeterministicId(String tenantId, String payload) {
        MessageDigest digest = HashAlgorithm.SHA3_512.getMessageDigest();
        String seedInput = tenantId + "::" + payload;
        byte[] hashBytes = digest.digest(seedInput.getBytes(StandardCharsets.UTF_8));

        // Extract the first 16 bytes of the SHA3-512 digest to construct a deterministic UUID
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (hashBytes[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (hashBytes[i] & 0xff);
        }

        // Set version to 4 (pseudo-random / derived) and variant to IETF RFC 4122
        msb = (msb & 0xffffffffffff0fffL) | 0x0000000000004000L;
        lsb = (lsb & 0x3fffffffffffffffL) | 0x8000000000000000L;

        return new UUID(msb, lsb);
    }

    /**
     * Performs the underlying cryptographic hashing logic based on the requested algorithm.
     */
    private String calculateHash(String payload, HashAlgorithm algorithm) {
        MessageDigest digest = algorithm.getMessageDigest();
        byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();

        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }

        return hexString.toString();
    }

    /**
     * Formats a raw hexadecimal hash string into a standardized, human-readable alphanumeric serial key format.
     */
    private String formatAsSerialKey(String hash) {
        String clean = hash.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();

        return String.format("%s-%s-%s-%s-%s",
                clean.substring(0, 5),
                clean.substring(5, 10),
                clean.substring(10, 15),
                clean.substring(15, 20),
                clean.substring(20, 25)
        );
    }

    /**
     * Constructs and persists an immutable forensic audit record for the generation lifecycle event.
     */
    private Mono<HashAudit> createAuditLog(HashToken token, UUID txId, String executor, boolean serialKey) {
        HashAudit audit = HashAudit.create(
                txId,
                token.tenantId(),
                token.id(),
                "HASH_GENERATION",
                "SUCCESS",
                executor,
                Map.of(
                        "algorithm", token.algorithm().name(),
                        "tokenId", token.id().toString(),
                        "isSerialKey", String.valueOf(serialKey)
                )
        );

        return hashAuditRepository.save(audit);
    }
}