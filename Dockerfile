# ==============================================================================================
# /**
#  * @file        Dockerfile
#  * @module      Thinklab Hash Service Container Packaging Manifest
#  * @version     v3.5.0-NASA-SRE-PROD-STABLE
#  * @description Enterprise-grade, multi-stage Docker build optimized for Micronaut 4 AOT.
#  *              Implements zero-trust runtime environments using Google Distroless.
#  *              Uses standard distribution packaging (Thin JAR + Libs) to avoid build.gradle modifications.
#  *
#  * @architectural_directives
#  *   1. Immutability: Deterministic build process with strict dependency caching.
#  *   2. Minimal Attack Surface: Non-root execution with zero shell access in runtime.
#  *   3. Low Latency: Pre-configured with ZGC and generational garbage collection.
#  *
#  * @maintainer  Thinklab Core Infrastructure & High-Assurance Engineering Team
#  */
# ==============================================================================================

# ==============================================================================================
# /**
#  * @stage       1: BUILD (Dependency Resolution & AOT Compilation)
#  * @image       gradle:8.7-jdk21-alpine
#  */
# ==============================================================================================
FROM gradle:8.7-jdk21-alpine AS builder

# Set the working directory within the build container
WORKDIR /home/gradle/src

# ----------------------------------------------------------------------------------------------
# /**
#  * @directive   Layer Caching & Dependency Resolution
#  * @description Isolates manifests to leverage Docker layer caching. Prevents re-downloading
#  *              the internet unless dependency trees are explicitly modified.
#  */
# ----------------------------------------------------------------------------------------------
COPY --chown=gradle:gradle build.gradle settings.gradle* gradle.properties* ./
RUN gradle dependencies --no-daemon || true

# ----------------------------------------------------------------------------------------------
# /**
#  * @directive   Source Code Ingestion
#  */
# ----------------------------------------------------------------------------------------------
COPY --chown=gradle:gradle src ./src

# ----------------------------------------------------------------------------------------------
# /**
#  * @directive   Ahead-of-Time (AOT) Compilation Engine & Distribution
#  * @description Skips test suites during container assembly.
#  *              Uses 'installDist' instead of 'build' to extract all third-party dependencies
#  *              alongside the application JAR without requiring the Shadow plugin.
#  */
# ----------------------------------------------------------------------------------------------
RUN gradle installDist -x test --no-daemon

# ----------------------------------------------------------------------------------------------
# /**
#  * @directive   Artifact Standardization & Extraction
#  * @description Gathers the compiled application JAR and all dependency JARs into a single
#  *              flat directory (/app-libs) for easy transfer to the runtime layer.
#  */
# ----------------------------------------------------------------------------------------------
RUN mkdir -p /app-libs && find build/install -path '*/lib/*.jar' -exec cp {} /app-libs/ \;

# ==============================================================================================
# /**
#  * @stage       2: RUNTIME (Zero-Trust, Minimal Footprint)
#  * @image       gcr.io/distroless/java21-debian12:nonroot
#  * @description Stripped-down OS containing only the JVM and its essential dependencies.
#  */
# ==============================================================================================
FROM gcr.io/distroless/java21-debian12:nonroot AS runtime

LABEL maintainer="Thinklab Core Infrastructure & High-Assurance Engineering Team"
LABEL version="v3.5.0-NASA-SRE-PROD-STABLE"
LABEL description="Thinklab Hash Service - Mission-Critical Reactive Micronaut 4 Runtime"
LABEL enviroment="Personal Home-Lab for Development"
LABEL git-repo="https://github.com/fernan-89/micronaut-hash-token-registry-service"

WORKDIR /app

# Inject all validated application and dependency JARs from the builder stage
COPY --from=builder --chown=nonroot:nonroot /app-libs/ /app/

# ----------------------------------------------------------------------------------------------
# /**
#  * @directive   JVM Tuning & Environment Topology
#  * @param       -XX:MaxRAMPercentage=75.0 : Respects Kubernetes/Docker memory cgroups.
#  * @param       -XX:+UseZGC -XX:+ZGenerational : Ultra-low latency GC, optimal for Netty/Reactor.
#  * @param       -XX:+UseStringDeduplication : Reduces memory footprint for identical strings.
#  */
# ----------------------------------------------------------------------------------------------
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -XX:+UseZGC -XX:+ZGenerational -XX:+UseStringDeduplication"
ENV MICRONAUT_SERVER_PORT=8080

# Fallback topology URIs (Should be overridden via Kubernetes Secrets in production orchestration)
ENV MONGODB_URI="mongodb://localhost:27017/thinklab_hash_db"

# Expose the non-blocking Netty port
EXPOSE ${MICRONAUT_SERVER_PORT}

# ----------------------------------------------------------------------------------------------
# /**
#  * @directive   Execution (PID 1)
#  * @description Executes via Classpath (-cp) instead of standard Fat JAR (-jar).
#  *              Loads all JARs in the /app/ directory and explicitly invokes the Application
#  *              Main-Class, bypassing the need for a pre-configured MANIFEST.MF.
#  */
# ----------------------------------------------------------------------------------------------
ENTRYPOINT ["java", "-cp", "/app/*", "com.thinklab.Application"]