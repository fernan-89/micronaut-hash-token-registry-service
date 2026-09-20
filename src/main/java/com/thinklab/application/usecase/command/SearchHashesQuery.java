package com.thinklab.application.usecase.command;

import com.thinklab.domain.valueobject.HashStatus;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Application Query: Encapsulates multi-dimensional search criteria for {@link com.thinklab.domain.model.HashToken}
 * collections (Convention B search behavior qualifier — {@code retrieve/search}).
 *
 * <p>Unlike {@link ListHashesQuery} (status-only filtering), this query additionally supports filtering
 * by the originating {@code sourceService}, matching the blueprint's Convention B {@code /v1/hashes/search}
 * contract while remaining strictly tenant-scoped for multi-tenant isolation.
 *
 * @param tenantId      The unique identifier of the tenant requesting the search. Must not be blank.
 * @param sourceService Optional originating microservice filter.
 * @param status        Optional lifecycle status filter.
 * @param page          The zero-indexed page number. Defaults to 0 if null.
 * @param size          The number of items per page (bounded between 1 and 100). Defaults to 20 if null.
 *
 * @author ThinkLab
 * @version 1.0.0
 * @since 1.0
 */
@Slf4j
@Introspected
public record SearchHashesQuery(
        @NotBlank(message = "Tenant ID is mandatory for security isolation")
        @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "Tenant ID contains invalid characters")
        String tenantId,

        @Nullable
        String sourceService,

        @Nullable
        HashStatus status,

        @Min(value = 0, message = "Page index must be greater than or equal to 0")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100 to prevent resource exhaustion")
        Integer size
) {

    public SearchHashesQuery {
        Objects.requireNonNull(tenantId, "Application constraint violated: tenantId cannot be null.");

        tenantId = tenantId.trim();
        sourceService = (sourceService != null && !sourceService.isBlank()) ? sourceService.trim() : null;

        if (tenantId.isBlank()) {
            log.error("[ACTION: SEARCH_HASHES_VALIDATION] - CRITICAL: Pipeline aborted due to missing tenant context in query.");
            throw new IllegalArgumentException("Application constraint violated: Tenant ID is mandatory for security isolation.");
        }

        page = (page == null) ? 0 : page;
        size = (size == null) ? 20 : size;
    }
}
