package com.payflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.exception.ErrorCode;
import com.payflow.filter.TracingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * OAuth2 resource-server security (Rule 14) and HTTP hardening (mentor feedback 22.e).
 *
 * <p>The service is a stateless resource server: every {@code /api/**} call must present a
 * valid Keycloak-issued JWT. Realm roles are mapped to authorities by
 * {@link KeycloakRealmRoleConverter}. Method-level security ({@code @PreAuthorize}) enforces
 * fine-grained authorization on controllers. Public endpoints are limited to health checks and
 * API documentation.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final PayflowProperties properties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(PayflowProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless JWT API; no session/cookies
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                // Auth failures also return RFC 7807 ProblemDetail, keeping the error contract uniform.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeProblem(response, request, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeProblem(response, request, ErrorCode.FORBIDDEN)))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.SAME_ORIGIN))
                        .addHeaderWriter((request, response) ->
                                response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")));

        return http.build();
    }

    /** Wires the Keycloak realm-role converter into the JWT authentication conversion. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(parseOrigins(properties.security().corsAllowedOrigins()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id", "traceparent"));
        config.setExposedHeaders(List.of("X-Trace-Id", "X-Span-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Serialises an {@link ErrorCode} as an RFC 7807 ProblemDetail for security failures. */
    private void writeProblem(HttpServletResponse response, HttpServletRequest request, ErrorCode code)
            throws java.io.IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.getStatus(), code.getDefaultMessage());
        problem.setTitle(code.getStatus().getReasonPhrase());
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", code.name());
        problem.setProperty("traceId", MDC.get(TracingFilter.TRACE_ID_KEY));
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }

    private List<String> parseOrigins(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
