package com.systemdesign.ecommerce.config;

import com.systemdesign.ecommerce.module.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║           Spring Security Configuration                      ║
 * ╚══════════════════════════════════════════════════════════════╝
 *
 * SYSTEM DESIGN: Stateless Security
 *   SessionCreationPolicy.STATELESS: Spring Security never creates
 *   an HTTP session. No JSESSIONID cookie is issued.
 *   Authentication state lives ONLY in the JWT token.
 *
 *   Benefits:
 *   - Horizontally scalable: any server instance can handle any request
 *   - No session store needed (no sticky sessions / session replication)
 *   - Perfect for REST APIs and microservices
 *
 * DESIGN PATTERN: Builder (SecurityFilterChain)
 *   HttpSecurity uses a fluent builder API to compose the filter chain.
 *
 * @EnableMethodSecurity: enables @PreAuthorize on controller methods.
 *   e.g., @PreAuthorize("hasRole('ADMIN')") on admin endpoints.
 *   Defense in depth: route-level AND method-level security.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    // ── Security Filter Chain ─────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // ── CSRF: disabled for stateless REST APIs ───────────────
            // CSRF attacks target session cookies. We use JWT (not cookies) → no CSRF risk.
            .csrf(AbstractHttpConfigurer::disable)

            // ── CORS: configured via CorsConfigurationSource bean ────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Session: stateless (no HttpSession created) ──────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Authorization Rules ───────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Public endpoints — no token required
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()

                // Swagger UI — permit the root redirect + all sub-paths + webjars
                // /swagger-ui.html      → redirects to /swagger-ui/index.html
                // /swagger-ui/**        → the actual UI assets
                // /v3/api-docs/**       → OpenAPI JSON spec
                // /webjars/**           → swagger-ui JS/CSS served via webjars
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/v3/api-docs.yaml",
                    "/webjars/**"
                ).permitAll()

                // Actuator — health + info (K8s liveness/readiness probes)
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()

                // Admin endpoints — require ADMIN role
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // ── JWT Filter: runs before UsernamePasswordAuthenticationFilter ──
            // This is where token extraction + validation happens per request
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            .build();
    }

    // ── Password Encoder ─────────────────────────────────────────

    /**
     * BCrypt password encoder.
     * strength=12: ~250ms to hash — strong brute-force resistance.
     *
     * SYSTEM DESIGN: Why BCrypt over SHA-256?
     *   SHA-256 is fast (millions of hashes/second) → brute forceable.
     *   BCrypt is intentionally slow (cost factor) → brute force takes years.
     *   Cost 12 = 2^12 = 4096 rounds internally.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // ── Authentication Manager ────────────────────────────────────

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ── CORS Configuration ────────────────────────────────────────

    /**
     * CORS: Cross-Origin Resource Sharing
     *
     * SYSTEM DESIGN: Why configure CORS on backend?
     *   Browser blocks requests from http://localhost:4200 to http://localhost:8080
     *   (different ports = different origins) unless backend explicitly allows it.
     *
     *   In production: use specific domain ("https://ecommerce.com"), not "*".
     *   Wildcard "*" is insecure in production.
     *
     *   In Docker + Nginx: Nginx proxies /api → backend on same origin → no CORS.
     *   CORS config here is for local dev (Angular dev server → Spring Boot directly).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
            "http://localhost:4200",   // Angular dev server
            "http://localhost:4201"    // secondary dev port
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);  // preflight cache for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
