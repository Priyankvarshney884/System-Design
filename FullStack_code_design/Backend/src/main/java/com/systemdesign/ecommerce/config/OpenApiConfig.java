package com.systemdesign.ecommerce.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║          OpenAPI / Swagger UI Configuration                   ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * Swagger UI URL: http://localhost:8080/swagger-ui.html
 * OpenAPI JSON:   http://localhost:8080/v3/api-docs
 *
 * SYSTEM DESIGN: API Documentation as a first-class concern
 *   - Every endpoint is self-documenting via annotations
 *   - Clients (Angular) can generate TypeScript types from the OpenAPI spec
 *   - In CI/CD: run schema diff to detect breaking API changes before deploy
 *
 * JWT Security Scheme:
 *   Adds "Authorize" button to Swagger UI.
 *   Enter "Bearer <token>" once → all requests include the header.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(
                        new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(
                        new Components().addSecuritySchemes(
                                SECURITY_SCHEME_NAME, jwtSecurityScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title("E-Commerce API — System Design Showcase")
                .description("""
                        Production-grade e-commerce REST API demonstrating:
                        - GoF Design Patterns (Strategy, Observer, Builder, Factory...)
                        - System Design (Caching, Rate Limiting, Circuit Breaker, CQRS)
                        - Java 21 + Spring Boot 3 best practices
                        """)
                .version("v1.0")
                .contact(new Contact()
                        .name("System Design Learning Project")
                        .url("https://github.com/your-repo"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT"));
    }

    /**
     * JWT Bearer token scheme.
     * Frontend sends: Authorization: Bearer <access_token>
     * Spring Security reads this in JwtAuthenticationFilter.
     */
    private SecurityScheme jwtSecurityScheme() {
        return new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Enter your JWT access token (without 'Bearer ' prefix)");
    }
}
