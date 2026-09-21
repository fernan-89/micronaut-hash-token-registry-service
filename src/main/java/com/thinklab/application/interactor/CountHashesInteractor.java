package com.thinklab.application.interactor;

import com.thinklab.application.port.in.CountHashesUseCase;
import com.thinklab.application.port.out.HashTokenRepositoryPort;
import com.thinklab.domain.valueobject.HashStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.Objects;

/** Counts hashes for the paginated listing endpoints; validates the tenant before touching the store. */
@Singleton
public class CountHashesInteractor implements CountHashesUseCase {

    private final HashTokenRepositoryPort hashTokenRepository;

    @Inject
    public CountHashesInteractor(HashTokenRepositoryPort hashTokenRepository) {
        this.hashTokenRepository = Objects.requireNonNull(hashTokenRepository, "Application constraint violated: HashTokenRepositoryPort cannot be null.");
    }

    @Override
    public Mono<Long> execute(String tenantId, String sourceService, HashStatus status) {
        Objects.requireNonNull(tenantId, "Application constraint violated: tenantId cannot be null.");
        return hashTokenRepository.countByFilters(tenantId.trim(), sourceService, status);
    }
}
