package com.example.bank.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8080/swagger-ui.html
 * To try protected endpoints: call /api/auth/login, click "Authorize", and paste
 * the accessToken (without the "Bearer " prefix).
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI bankOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Core Banking API")
                        .version("v1")
                        .description("""
                                Learning project: accounts, double-entry ledger, transfers.
                                Money endpoints need an `Idempotency-Key` header (any unique string, e.g. a UUID).
                                Errors use RFC 9457 problem details with a stable `code` field."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                // Applies the token to every operation; register/login simply ignore it.
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
