package com.thinklab.domain.valueobject;

import com.thinklab.domain.exception.InvalidHashStatusException;

import java.util.Objects;

/**
 * Domain Value Object: State machine representing the lifecycle of a HashToken entity.
 *
 * <p><b>Architectural Role:</b>
 * This enumeration acts as the central state machine and gatekeeper for HashToken lifecycles.
 * It resides deeply within the Domain Layer and encapsulates the strict business rules governing
 * valid and invalid state transitions, ensuring compliance and data integrity across the platform.
 *
 * <p><b>Contractual Obligations:</b>
 * <ul>
 *   <li><b>Deterministic Transitions:</b> Strictly defines legally permitted state shifts.</li>
 *   <li><b>Zero Trust / Terminal States:</b> Treats {@link #REVOKED} as an absolute terminal state with no exit path.</li>
 *   <li><b>Defensive Integrity:</b> Defensively rejects null inputs and actively warns on idempotent transitions.</li>
 * </ul>
 *
 * <p><b>Concurrency & Thread-Safety:</b>
 * This enumeration and its transition functions are completely stateless, immutable, and globally thread-safe.
 *
 * <p><b>Reactive Behavior:</b>
 * The transition evaluation methods ({@link #validateTransitionTo(HashStatus)} and {@link #canTransitionTo(HashStatus)})
 * are non-blocking, CPU-bound pure functions. They are designed to be invoked directly within Project Reactor
 * operators (e.g., {@code .map()}, {@code .doOnNext()}) without risk of blocking the Netty EventLoop.
 *
 * @version 1.0.0
 * @since 1.0
 */
public enum HashStatus {

    /**
     * Token is fully functional and actively available for cryptographic validation.
     */
    ACTIVE,

    /**
     * Token is temporarily suspended. It can be reactivated to {@link #ACTIVE} or permanently {@link #REVOKED}.
     */
    INACTIVE,

    /**
     * Token is permanently disabled (e.g., due to a security breach or compliance mandate).
     * <b>This is a terminal state.</b> No further transitions are permitted.
     */
    REVOKED;

    /**
     * Validates if the transition from the current state to the target state is legally permitted.
     *
     * <p><b>Contract:</b> This method serves as a fail-fast compliance check. It must be called
     * prior to persisting any state mutation to the repository.
     *
     * @param targetStatus The desired state to transition to.
     * @throws InvalidHashStatusException if the transition violates business compliance rules
     *                                    or if the transition is unnecessarily idempotent.
     * @throws NullPointerException if the {@code targetStatus} is null.
     */
    public void validateTransitionTo(HashStatus targetStatus) {
        Objects.requireNonNull(targetStatus, "Target HashStatus must not be null for transition validation.");

        if (this == targetStatus) {
            throw new InvalidHashStatusException(String.format(
                    "Idempotency Violation: The HashToken is already in the [%s] state.", this));
        }

        if (!canTransitionTo(targetStatus)) {
            throw new InvalidHashStatusException(String.format(
                    "Compliance Violation: Illegal state transition from [%s] to [%s].", this, targetStatus));
        }
    }

    /**
     * Evaluates if the transition from the current state to the target state is legally permitted
     * without throwing an exception.
     *
     * @param targetStatus The desired state to evaluate.
     * @return {@code true} if the transition is allowed by the state machine; {@code false} otherwise.
     */
    public boolean canTransitionTo(HashStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        return switch (this) {
            case ACTIVE -> targetStatus == INACTIVE || targetStatus == REVOKED;
            case INACTIVE -> targetStatus == ACTIVE || targetStatus == REVOKED;
            case REVOKED -> false;
        };
    }
}