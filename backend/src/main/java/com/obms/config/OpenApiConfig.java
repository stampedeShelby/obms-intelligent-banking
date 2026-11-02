package com.obms.config;

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
 * OpenAPI document describing the OBMS API.
 *
 * <p>Declaring the bearer scheme here means the Swagger UI can hold a token and
 * exercise protected endpoints, which is how the API is demonstrated and how
 * the API test cases in the Phase-2 report were captured.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI obmsOpenApi() {
        final String scheme = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("OBMS - Intelligent Online Banking Management System API")
                        .version("1.0.0")
                        .description("""
                                REST API for OBMS: a layered Spring Boot banking application with
                                explainable fraud detection, spending analytics, a financial
                                wellness score, a what-if simulator and a financial copilot.

                                This is a SIMULATED banking environment built as an academic
                                project. It connects to no real payment network, holds no real
                                customer data, and moves no real funds.

                                Authentication is two-factor. POST /api/auth/login verifies the
                                password and returns a pre-auth token; POST /api/auth/verify-otp
                                exchanges that plus the emailed code for a bearer access token.
                                """)
                        .contact(new Contact().name("OBMS project team"))
                        .license(new License().name("Academic project").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .name(scheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from POST /api/auth/verify-otp")));
    }
}
