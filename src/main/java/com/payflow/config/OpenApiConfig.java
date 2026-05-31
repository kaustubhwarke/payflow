package com.payflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Builds the OpenAPI 3 document that backs Swagger UI (Rule 19: strong API contract).
 * Declares a global bearer-JWT security scheme so the "Authorize" button in Swagger UI lets
 * users paste a Keycloak access token and exercise secured endpoints.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI payflowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayFlow API")
                        .description("Production-ready UPI-style payment microservice. "
                                + "Authenticate with a Keycloak-issued JWT (realm: payflow).")
                        .version("v1")
                        .contact(new Contact().name("PayFlow Team").email("team@payflow.com"))
                        .license(new License().name("Apache-2.0")))
                .servers(List.of(new Server().url("/").description("Default")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Keycloak access token")));
    }
}
