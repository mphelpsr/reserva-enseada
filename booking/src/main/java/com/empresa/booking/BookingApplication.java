package com.empresa.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }

    // Beans de function (apiHandler, operatorEventsConsumer — ver infra/lambda.tf)
    // entram na Fase 3.4. releaseExpiredHoldsJob (T046) já existe como
    // @Component (ReleaseExpiredHoldsJob) — nome de bean padrão do Spring.
}
