package com.thinklab.application.port.in;

import com.thinklab.domain.valueobject.HashStatus;
import reactor.core.publisher.Mono;

/**
 * Application Port: counts the tenant hashes matching optional filters, so paginated responses can report an
 * accurate {@code totalElements}. Read-only (CQRS query side).
 */
public interface CountHashesUseCase {

    Mono<Long> execute(String tenantId, String sourceService, HashStatus status);
}
