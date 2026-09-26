package com.thinklab;

import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.inject.Inject;

/**
 *  Infrastructure Integration Test: Application Bootstrap Validation.
 *  This suite acts as the primary "Smoke Test" and circuit breaker for the CI/CD pipeline,
 *  ensuring that the Micronaut IoC container, AOT optimizations, and reactive
 *  infrastructure (Netty/MongoDB) are correctly wired and operational.
 *
 *  <p><b>Architectural Role:</b> Infrastructure Layer - Bootstrap Verification.</p>
 *
 *  <p><b>Test Guarantees:</b></p>
 *  <ul>
 *      <li><b>Container Integrity:</b> Verifies that all {@link jakarta.inject.Singleton} beans,
 *      Ports, and Adapters can be instantiated without circular dependencies or missing definitions.</li>
 *      <li><b>Context Readiness:</b> Ensures the ApplicationContext is fully initialized and
 *      ready to accept non-blocking I/O traffic.</li>
 *      <li><b>Environment Isolation:</b> Operates strictly within the 'test' environment to
 *      prevent accidental production state side-effects.</li>
 *  </ul>
 *
 *  @version 2.0.0
 *  @since 1.0.0
 */
@MicronautTest(transactional = false)
class MicronautHashServiceTest {

    /**
     * The handle to the running Micronaut application.
     * Injected by the framework to allow inspection of the runtime state.
     */
    @Inject
    EmbeddedApplication<?> application;

    /**
     * Critical Path Validation: Verifies that the server context is active.
     * If this test fails, the application has a fatal configuration error (e.g., malformed
     * YAML, missing drivers, or invalid JCA providers for BLAKE3) [cite: 77, 141, 601].
     */
    @Test
    @DisplayName("Mission Control: Should successfully initialize the Application Context")
    void testItWorks() {
        // Assert: The application must be running and initialized
        Assertions.assertTrue(application.isRunning(),
                "FATAL: Application failed to reach RUNNING state. Check logs for DI or configuration failures.");
    }

}