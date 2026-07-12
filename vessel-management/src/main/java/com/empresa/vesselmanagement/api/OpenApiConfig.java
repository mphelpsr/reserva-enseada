package com.empresa.vesselmanagement.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** T061. Metadados básicos do OpenAPI — o resto é introspectado dos @RestController. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vesselManagementOpenApi() {
        return new OpenAPI().info(new Info()
                .title("vessel-management")
                .description("Painel do proprietário — cadastro de embarcações, disponibilidade, "
                        + "rodízio e limite de vagas (specs/001-vessel-management).")
                .version("v1"));
    }
}
