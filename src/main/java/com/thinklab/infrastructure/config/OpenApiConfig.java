package com.thinklab.infrastructure.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;

/**
 * Infrastructure Component: OpenAPI 3.0 Documentation Metadata.
 *
 * <p><b>Architectural Role:</b>
 * Centralizes the global API contract definition. During the Ahead-of-Time (AOT)
 * compilation phase, the 'micronaut-openapi' AST processor parses these annotations
 * to statically generate the official swagger.yml specification.
 *
 * <p><b>Zero-Trust & Governance:</b>
 * Ensures all API consumers rely on a cryptographically verifiable and version-locked
 * interface contract. No reflection is used at runtime to serve this documentation.
 *
 * @author Thinklab Core Infrastructure Team
 * @version 3.4.1-NASA-SRE-PROD
 * @since 1.0
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Thinklab Hash Token Registry Service Domain",
                version = "v4.0.0",
                description = "BIAN-aligned Service Domain (Control Record: HashToken) for cryptographic hash orchestration, reactive persistence, and forensic auditing. All routes follow the /hash-token-registry/v1/{behavior-qualifier} convention (initiate, retrieve, control). Built on Zero-Trust principles with Project Reactor.",
                contact = @Contact(
                        name = "Thinklab SRE & Security Operations",
                        email = "sre-core@thinklab.com",
                        url = "https://engineering.thinklab.com"
                ),
                license = @License(
                        name = "Proprietary & Confidential - Thinklab Internal Only",
                        url = "https://thinklab.com/security/compliance"
                )
        )
)
public class OpenApiConfig {
    // Empty class serving strictly as an AST metadata anchor for Swagger generation.
}