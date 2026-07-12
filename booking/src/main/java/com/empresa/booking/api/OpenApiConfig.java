package com.empresa.booking.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** T060. Metadados básicos do OpenAPI — o resto é introspectado dos @RestController. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("booking")
                .description("App do comprador — consulta de disponibilidade, reserva, "
                        + "pagamento e cancelamento (specs/002-booking).")
                .version("v1"));
    }
}
