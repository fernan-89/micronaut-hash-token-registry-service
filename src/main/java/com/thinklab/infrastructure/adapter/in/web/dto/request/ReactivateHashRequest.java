package com.thinklab.infrastructure.adapter.in.web.dto.request;

import com.thinklab.application.usecase.command.ReactivateHashCommand;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Objects;
import java.util.UUID;

/**
 * Infrastructure DTO: Web request payload for the reactivation of an INACTIVE {@link com.thinklab.domain.model.HashToken}
 * (BIAN Behavior Qualifier: {@code control/reactivate}).
 *
 * <p>The executor identity is no longer accepted in the body — it is extracted from the mandatory
 * {@code X-Executor} header by the controller, following the platform-wide cross-service convention.
 *
 * @param reason The comprehensive business justification provided for restoring the operational status of the token.
 *
 * @author ThinkLab
 * @since 1.0
 */
@Serdeable
@Introspected
@Schema(
        name = "ReactivateHashRequest",
        description = "Mandatory forensic payload required to restore an INACTIVE hash registry back to its ACTIVE operational status. Executor identity is supplied via the X-Executor header."
)
public record ReactivateHashRequest(

        @NotBlank(message = "A business reason for reactivation is mandatory for forensic traceability.")
        @Size(min = 5, max = 500, message = "The reactivation justification must be between 5 and 500 characters.")
        @Schema(description = "Detailed business justification for the operational restoration.", example = "System maintenance completed, restoring primary keys.")
        String reason
) {

    /**
     * Translates the strictly validated web request payload into a domain-compliant Application Command.
     *
     * @param hashId   The universally unique identifier (UUID) of the target hash, extracted securely from the HTTP Path.
     * @param executor Executor identity resolved from the {@code X-Executor} header.
     * @return A pristine, immutable {@link ReactivateHashCommand} ready for Use Case execution.
     * @throws NullPointerException if the injected {@code hashId} is null.
     */
    public ReactivateHashCommand toCommand(UUID hashId, String executor) {
        Objects.requireNonNull(hashId, "Infrastructure constraint violated: Target Hash UUID must not be null during command translation.");
        return new ReactivateHashCommand(hashId, executor, this.reason);
    }
}
