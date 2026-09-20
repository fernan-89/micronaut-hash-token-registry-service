package com.thinklab.application.port.in;

import com.thinklab.application.usecase.command.SearchHashesQuery;
import com.thinklab.domain.model.HashToken;
import reactor.core.publisher.Flux;

/**
 * Application Port: Input boundary for the multi-dimensional search of {@link HashToken} registries
 * (Convention B behavior qualifier — {@code retrieve/search}).
 *
 * @author ThinkLab
 * @version 1.0.0
 * @since 1.0
 */
public interface SearchHashesUseCase {

    /**
     * Orchestrates the paginated, reactive search of hash token registries by source service and/or status,
     * strictly scoped to a tenant boundary.
     *
     * @param query The {@link SearchHashesQuery} encapsulating tenant context, filter criteria, and pagination metadata. Must not be null.
     * @return A {@link Flux} emitting a backpressure-aware stream of matching {@link HashToken} domain aggregates.
     * @throws NullPointerException if the provided query is null.
     */
    Flux<HashToken> execute(SearchHashesQuery query);
}
