# Thinklab Hash Service

**Version:** 4.0.0

**Status:** Reference implementation — portfolio project

## Overview

The Thinklab Hash Service is a microservice for the authoritative management of the complete lifecycle of cryptographic hash tokens. Developed using Java 21 and the Micronaut Framework, this service utilizes strict Hexagonal Architecture (Ports and Adapters) combined with a fully Reactive Stack to ensure high throughput, zero-blocking I/O, and absolute structural maintainability for enterprise-scale identity and security ecosystems.

Designed under strict Site Reliability Engineering (SRE) and Zero-Trust principles, the service features deterministic containerization, Ahead-of-Time (AOT) bytecode optimizations, and resilient telemetry pipelines capable of surviving transient infrastructure failures.

## Technology Stack

* **Runtime:** Java 21 LTS (Project Loom / Virtual Threads enabled)
* **Framework:** Micronaut 4.4.2 (AOT Optimized, reflection-free DI and Serde)
* **Reactive Engine:** Project Reactor (Mono / Flux)
* **Persistence:** Reactive MongoDB utilizing BSON Binary UUID Subtype 4 for optimized indexing
* **Observability:** OpenTelemetry (W3C Standard), SLF4J, Logback (Async), SRE Forensics, and Project Reactor Hooks
* **Security & Containerization:** Google Distroless (nonroot), Read-Only Root Filesystems, Zero-Trust Capabilities
* **Testing Suite:** JUnit 5, Mockito and Reactor Test (unit tests)
* **Documentation:** OpenAPI 3.0 / Swagger (Generated statically at compile-time)

---

## Architectural Model & Engineering Mandates

The project implements a strict three-tier hexagonal structure to ensure that the Core Domain remains framework-agnostic and immune to infrastructure volatility:

```text
src/main/java/com/thinklab/
├── domain/                  # Core Business Logic (Rich Models, Value Objects, Pure Exceptions)
├── application/             # Orchestration & Use Cases (Input/Output Ports, Interactors)
└── infrastructure/          # External Integrations (REST Controllers, MongoDB Adapters, Telemetry)

```

### 1. Hexagonal & DTO Isolation Pattern (ADR-001, ADR-003, ADR-004)

The Core Domain is completely decoupled from web and persistence layers.

* **Domain Purity:** Domain records are devoid of `@Serdeable`, `@Schema`, or `@MappedEntity` annotations.
* **Projection Mapping:** All input/output crosses the boundary via explicit Data Transfer Objects (DTOs) utilizing static factory transformations (e.g., `fromDomain()`).
* **Resilient Exception Boundaries:** Structural decoupling of Business Exceptions from Infrastructure Failures. All errors are projected into standardized **RFC 7807 (Problem Details)** payloads via a unified `GlobalExceptionHandler`.

### 2. Identity Sovereignty & Partial State Mutations (ADR-002, ADR-005)

* **UUID Standardization:** Mandatory enforcement of native `java.util.UUID` for all primary and correlation identifiers to ensure optimal MongoDB BSON indexing and prevent type contamination.
* **Explicit Partial Updates:** Monolithic `.save()` operations are strictly reserved for aggregate creation. State transitions (e.g., Deactivate, Revoke) utilize targeted `$set` query updates (`@Id` bounded) via custom repository ports to eliminate unique ID collisions and optimize network I/O.

### 3. Proactive Initialization & Synchronous Barriers (ADR-006, ADR-007)

The application adheres to a "Fail-Fast before Port Binding" philosophy to protect Kubernetes routing.

* **Two-Phase Warmup:** A `StartupEvent` triggers a deterministic MongoDB ping to both the `admin` and dynamically parsed application databases, forcing SDAM topology discovery.
* **Synchronous Barriers:** The initialization thread is intentionally blocked (with timeouts) until all external dependencies are fully validated.
* **Passive Circuit Breaking:** If the database is unreachable, progressive backoffs are applied. If all retries exhaust, the JVM degrades gracefully and shuts down before the HTTP server ever starts, signaling Kubernetes to reschedule the pod.

### 4. Distributed Telemetry & Privacy-Preserving Logging (ADR-008, ADR-009, ADR-010)

End-to-end traceability across non-blocking asynchronous thread boundaries is guaranteed without violating data privacy regulations.

* **W3C Trace Context:** Natively implements the OpenTelemetry standard for distributed tracing. The edge filters dynamically parse `traceparent` headers without hard-coupling to SDK classes, eradicating runtime `NoClassDefFoundError` risks.
* **Reactive MDC Bridge:** A global Project Reactor operator hook explicitly synchronizes the reactive `Context` with the active thread's SLF4J `MDC`. This ensures no `[trace=NONE]` logs occur during Netty/Reactor worker thread hops.
* **Privacy by Design (LGPD/GDPR):** The ingress `TraceIdFilter` obfuscates the final IP segment (e.g., `192.168.1.***`) and truncates massive User-Agents before they hit the logging pipeline, stripping PII classification from observability platforms.

### 5. Zero-Trust Containerization & AOT Packaging (ADR-011)

The deployment artifact and infrastructure manifests are hardened against supply chain attacks and runtime vulnerabilities.

* **Eradication of Legacy Plugins:** The build relies exclusively on Micronaut native AOT (Ahead-of-Time) plugins, ensuring deterministic dependency resolution, compile-time service-loading, and zero legacy classpath conflicts.
* **Distroless & Non-Root Execution:** The production image (`gcr.io/distroless/java21-debian12:nonroot`) executes strictly as UID 65532. It contains no shell (`/bin/sh`) or OS package managers, mathematically neutralizing reverse-shell and container escape injections.
* **Immutable Filesystems:** Kubernetes deployments enforce `readOnlyRootFilesystem: true` and drop `ALL` Linux capabilities, establishing absolute immutability during runtime.

### 6. Execution Context Forensics & SRE Telemetry (ADR-012)

Advanced runtime observability guarantees zero blind spots throughout the entire application lifecycle.

* **Pre-Flight Boot Context & Thread Propagation:** Programmatically injects a baseline `SYSTEM-BOOT` context into the SLF4J MDC at `Application.main()`, asserting it across worker pools and reactive boundaries to eradicate `[NONE]` logs during warmup.
* **Automated Environment Probing:** Natively scans filesystem signatures (`/.dockerenv`) and orchestration variables (`KUBERNETES_SERVICE_HOST`) at startup to explicitly emit the runtime execution tier (e.g., `Kubernetes Pod` vs `Bare-Metal`).
* **Edge Caller Forensics & Asynchronous Reverse DNS:** Enriches ingress requests by capturing target virtual hosts (`Host` header), request provenance (`Origin`/`Referer`), and external client hostnames via non-blocking asynchronous lookups bound to `Schedulers.boundedElastic()`.

---

## Operational Procedures

### Local Build and Execution

Ensure the local environment is aligned with the required Java 21 toolchain before execution.

```bash
# Clean, resolve dependencies via BOM, run AOT optimizations, and assemble Fat JAR
./gradlew clean build --refresh-dependencies

# Start the Reactive Service (Default Port: 8080)
./gradlew run

```

### Docker & Kubernetes (Cloud-Native Build)

The project utilizes a highly optimized Multi-Stage Dockerfile leveraging BuildKit layer caching.

```bash
# Build the Distroless Container Image
docker build -t thinklab-hash-token-registry-service:latest .

# Apply Kubernetes strict deployment manifest
kubectl apply -f k8s-deployment.yaml

```

### Infrastructure and API Endpoints

* **Health and Readiness Probes:** `http://localhost:8080/health`
* **Swagger UI (Interactive API Contract):** `http://localhost:8080/swagger-ui`
* **OpenAPI Specs (Raw YAML):** `http://localhost:8080/swagger/thinklab-hash-token-registry-service-domain-v4.0.0.yml`

### BIAN Behavior Qualifier Contract (`/hash-token-registry/v1`)

All mutations require the `X-Executor` header (`initiate` also requires `X-Tenant-Id` and `X-Source-Service`). There is no `DELETE` — `control/revoke` replaces the previous destructive verb with a terminal, idempotent `PUT`.

| Behavior Qualifier | Method & Path |
|---|---|
| initiate | `POST /hash-token-registry/v1/initiate` |
| retrieve (single) | `GET /hash-token-registry/v1/{id}/retrieve` |
| retrieve (collection) | `GET /hash-token-registry/v1/retrieve` |
| retrieve/search (Convention B) | `GET /hash-token-registry/v1/retrieve/search` |
| retrieve by status (Convention B) | `GET /hash-token-registry/v1/status/{status}/retrieve` |
| control/deactivate, reactivate, revoke | `PUT /hash-token-registry/v1/{id}/control/{action}` |
| audit-log/retrieve | `GET /hash-token-registry/v1/{id}/audit-log/retrieve` |
| full-profile/retrieve | `GET /hash-token-registry/v1/{id}/full-profile/retrieve` |

Example:

```bash
curl -X POST http://localhost:8080/hash-token-registry/v1/initiate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-prod-alpha-1" \
  -H "X-Source-Service: order-management-service" \
  -H "X-Executor: admin-user-01" \
  -d '{"payload":"raw-transaction-data-v1","algorithm":"SHA_256","asSerialKey":false}'
```

### E2E Testing (Postman)

A complete "Hash Lifecycle Suite" is available for Postman, dynamically integrated with the global telemetry hooks.

* The collection utilizes pre-request scripts to inject a fresh standard `traceparent` or `X-Trace-Id` into every call, mirroring service-mesh environments.
* Includes automated assertions (`pm.test`) to validate structural integrity, HTTP RFC 7807 problem details, and state machine transitions across the entire cryptographic token lifecycle.

## License

Proprietary - all rights reserved. See [LICENSE](LICENSE). This software is not open source.
