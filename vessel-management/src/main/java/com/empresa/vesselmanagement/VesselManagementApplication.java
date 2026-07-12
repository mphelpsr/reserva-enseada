package com.empresa.vesselmanagement;

import java.util.function.Supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class VesselManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(VesselManagementApplication.class, args);
    }

    // Placeholder para SPRING_CLOUD_FUNCTION_DEFINITION=apiHandler (infra/lambda.tf).
    // Substituido pelos controllers REST na Fase 3.4 (T047-T051).
    @Bean
    public Supplier<String> apiHandler() {
        return () -> "vessel-management api placeholder";
    }

    // Placeholder para SPRING_CLOUD_FUNCTION_DEFINITION=advisoryCalculationJob (infra/lambda.tf).
    // Substituido pelo AdvisoryCalculationJob na Fase 3.4 (T057).
    @Bean
    public Supplier<String> advisoryCalculationJob() {
        return () -> "vessel-management advisory job placeholder";
    }
}
