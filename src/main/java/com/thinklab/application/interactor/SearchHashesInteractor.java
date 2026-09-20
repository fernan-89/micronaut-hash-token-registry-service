package com.thinklab.application.interactor;

import com.thinklab.application.port.in.SearchHashesUseCase;
import com.thinklab.application.port.out.HashTokenRepositoryPort;
import com.thinklab.application.usecase.command.SearchHashesQuery;
import com.thinklab.domain.model.HashToken;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * Application Interactor: Implementation of the {@link SearchHashesUseCase} input port.
 *
 * <p>Branches across the tenant-scoped repository query methods depending on which optional
 * filters (source service, status) are present in the {@link SearchHashesQuery}.
 *
 * @author ThinkLab
 * @version 1.0.0
 * @since 1.0
 */
@Slf4j
@Singleton
public class SearchHashesInteractor implements SearchHashesUseCase {

    private final HashTokenRepositoryPort hashTokenRepository;

    @Inject
    public SearchHashesInteractor(HashTokenRepositoryPort hashTokenRepository) {
        this.hashTokenRepository = Objects.requireNonNull(hashTokenRepository, "Application constraint violated: HashTokenRepositoryPort cannot be null.");
    }

    @Override
    public Flux<HashToken> execute(SearchHashesQuery query) {
        Objects.requireNonNull(query, "Application constraint violated: SearchHashesQuery cannot be null.");

        Pageable pageable = Pageable.from(query.page(), query.size());
        String tenantId = query.tenantId();

        return Flux.defer(() -> {
                    log.info("[ACTION: SEARCH_HASHES] [TENANT: {}] [SOURCE: {}] [STATUS: {}] [PAGE: {}] [SIZE: {}] - Initiating multi-dimensional search pipeline.",
                            tenantId, query.sourceService(), query.status(), query.page(), query.size());

                    if (query.sourceService() != null && query.status() != null) {
                        return hashTokenRepository.findAllByTenantIdAndSourceServiceAndStatus(tenantId, query.sourceService(), query.status(), pageable);
                    }
                    if (query.sourceService() != null) {
                        return hashTokenRepository.findAllByTenantIdAndSourceService(tenantId, query.sourceService(), pageable);
                    }
                    if (query.status() != null) {
                        return hashTokenRepository.findAllByTenantIdAndStatus(tenantId, query.status(), pageable);
                    }
                    return hashTokenRepository.findAllByTenantId(tenantId, pageable);
                })
                .doOnComplete(() -> log.info("[ACTION: SEARCH_HASHES] [TENANT: {}] - Search stream completed successfully.", tenantId))
                .doOnError(error -> log.error("[ACTION: SEARCH_HASHES] [TENANT: {}] - CRITICAL: Search pipeline failed: {}", tenantId, error.getMessage(), error));
    }
}
