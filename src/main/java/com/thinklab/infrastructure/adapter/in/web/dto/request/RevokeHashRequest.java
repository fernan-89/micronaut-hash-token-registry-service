package com.thinklab.infrastructure.adapter.in.web.dto.request;

import com.thinklab.application.usecase.command.RevokeHashCommand;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Objects;
import java.util.UUID;

/**
 * Infrastructure DTO: Web request payload for the permanent and irreversible revocation of a {@link com.thinklab.domain.model.HashToken}
 * (BIAN Behavior Qualifier: {@code control/revoke}).
 *
 * <p>The executor identity is no longer accepted in the body — it is extracted from the mandatory
 * {@code X-Executor} header by the controller, following the platform-wide cross-service convention.
 *
 * @param reason The comprehensive business justification provided for permanently destroying the token's operational status.
 *
 * @author ThinkLab
 * @since 1.0
 */
@Serdeable
@Introspected
@Schema(
        name = "RevokeHashRequest",
        description = "Mandatory forensic payload required to permanently and irreversibly revoke a cryptographic token. Executor identity is supplied via the X-Executor header."
)
public record RevokeHashRequest(

        @NotBlank(message = "A detailed business reason for revocation is mandatory for forensic traceability.")
        @Size(min = 10, max = 500, message = "The permanent revocation justification must be between 10 and 500 characters to ensure detail.")
        @Schema(description = "Detailed business justification for the permanent revocation.", example = "Compromised payload source detected by automated monitoring system.")
        String reason
) {

    /**
     * Compact constructor to enforce programmatic fail-fast validation.
     *
     * @throws NullPointerException if any mandatory parameter is null.
     * @throws IllegalArgumentException if any mandatory string parameter is blank.
     */
    public RevokeHashRequest {
        Objects.requireNonNull(reason, "Edge Invariant Violation: Reason cannot be null.");

        if (reason.isBlank()) {
            throw new IllegalArgumentException("Edge Invariant Violation: Reason cannot be blank.");
        }
    }

    /**
     * Translates the strictly validated web request payload into a domain-compliant Application Command.
     *
     * @param hashId   The universally unique identifier (UUID) of the target hash, extracted securely from the HTTP Path.
     * @param executor Executor identity resolved from the {@code X-Executor} header.
     * @return A pristine, immutable {@link RevokeHashCommand} ready for Use Case execution.
     * @throws NullPointerException if the injected {@code hashId} is null.
     */
    public RevokeHashCommand toCommand(UUID hashId, String executor) {
        Objects.requireNonNull(hashId, "Infrastructure constraint violated: Target Hash UUID must not be null during command translation.");
        return new RevokeHashCommand(hashId, executor, this.reason);
    }
}
