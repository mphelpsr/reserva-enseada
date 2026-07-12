package com.empresa.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }

    // Beans de function (apiHandler, operatorEventsConsumer, releaseExpiredHoldsJob —
    // ver infra/lambda.tf) entram na Fase 3.4, junto dos casos de uso reais
    // (T038-T046) que eles expõem. Fase 3.1 é só o scaffold.
}
