package com.thinklab.infrastructure.adapter.in.web.dto.request;

import com.thinklab.application.usecase.command.GenerateHashCommand;
import com.thinklab.domain.valueobject.HashAlgorithm;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Objects;

/**
 * Infrastructure DTO: Web request payload for the generation of a cryptographic {@link com.thinklab.domain.model.HashToken}.
 *
 * <p><b>Architectural Role:</b>
 * This Data Transfer Object (DTO) acts as the formal, strongly-typed interface definition for the HTTP
 * generation endpoint (BIAN Behavior Qualifier: {@code initiate}). It serves as an Anti-Corruption Layer (ACL)
 * at the outermost edge of the system.
 *
 * <p><b>Contractual Obligations:</b>
 * <ul>
 * <li><b>Protocol Translation:</b> Decouples external API consumers from the internal, immutable
 *     Application Command structures. Changes in HTTP contracts do not bleed into the Domain.</li>
 * <li><b>Resource Exhaustion Mitigation (DoS Protection):</b> Enforces strict schema compliance and
 *     upper boundaries on payload sizes (max 10,000 characters) synchronously at the Netty HTTP edge,
 *     dropping malicious payloads before they consume business-layer memory or CPU cycles.</li>
 * <li><b>Header-Sourced Forensics:</b> {@code sourceService} and {@code executor} are no longer accepted
 *     in the body — they are extracted from the mandatory {@code X-Source-Service} and {@code X-Executor}
 *     headers by the controller, following the platform-wide cross-service convention.</li>
 * </ul>
 *
 * @param payload     The raw, bounded string data to be cryptographically processed.
 * @param algorithm   The specific cryptographic algorithm chosen for this operation.
 * @param asSerialKey Flag indicating if the cryptographic output should be structurally formatted as a serial key.
 *
 * @author ThinkLab
 * @since 1.0
 */
@Serdeable
@Introspected
@Schema(
        name = "GenerateHashRequest",
        description = "Mandatory payload required to initiate a new cryptographic token generation process. Tenant, source service, and executor context are supplied via headers (X-Tenant-Id, X-Source-Service, X-Executor)."
)
public record GenerateHashRequest(

        @NotBlank(message = "Cryptographic payload cannot be blank.")
        @Size(max = 10000, message = "Payload exceeds the absolute security limit of 10,000 characters.")
        @Schema(description = "Raw content to be hashed. Capped at 10,000 characters to prevent DoS. Sanitized (trimmed, special characters stripped) before hashing; the raw value is retained internally as originalPayload.", example = "raw-transaction-data-v1")
        String payload,

        @NotNull(message = "A cryptographic algorithm strategy must be explicitly specified.")
        @Schema(description = "The cryptographic algorithm strategy to be utilized.", example = "SHA_256")
        HashAlgorithm algorithm,

        @Schema(description = "If true, mathematically formats the final output into a segmented serial key.", defaultValue = "false")
        boolean asSerialKey
) {

    /**
     * Compact constructor to enforce programmatic fail-fast validation.
     *
     * <p><b>Contract:</b> While Jakarta Validation (JSR-380) secures the HTTP edge, this constructor
     * acts as a secondary defense-in-depth layer. It guarantees that even if this DTO is instantiated
     * programmatically (e.g., via message brokers or unit tests), it is impossible to create an invalid state.
     *
     * @throws NullPointerException if any mandatory parameter is null.
     * @throws IllegalArgumentException if any mandatory string parameter is blank.
     */
    public GenerateHashRequest {
        Objects.requireNonNull(payload, "Edge Invariant Violation: Payload cannot be null.");
        Objects.requireNonNull(algorithm, "Edge Invariant Violation: Cryptographic algorithm cannot be null.");

        if (payload.isBlank()) {
            throw new IllegalArgumentException("Edge Invariant Violation: Payload cannot be blank.");
        }
    }

    /**
     * Translates the strictly validated web request payload into a domain-compliant Application Command.
     *
     * <p><b>Contract:</b> This method acts as the secure translation bridge between the Volatile Transport
     * Protocol (HTTP) and the Immutable Application Use Case boundary.
     *
     * @param tenantId      Tenant context resolved from the {@code X-Tenant-Id} header.
     * @param sourceService Calling service identity resolved from the {@code X-Source-Service} header.
     * @param executor      Executor identity resolved from the {@code X-Executor} header.
     * @return A pristine, strictly validated {@link GenerateHashCommand} ready for asynchronous processing.
     */
    public GenerateHashCommand toCommand(String tenantId, String sourceService, String executor) {
        return new GenerateHashCommand(
                tenantId,
                this.payload,
                this.algorithm,
                sourceService,
                executor,
                this.asSerialKey
        );
    }
}
