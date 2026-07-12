package com.empresa.vesselmanagement;

import java.util.function.Consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.empresa.vesselmanagement.infrastructure.messaging.BookingEventsConsumer;
import com.empresa.vesselmanagement.jobs.AdvisoryCalculationJob;

@SpringBootApplication
public class VesselManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(VesselManagementApplication.class, args);
    }

    // SPRING_CLOUD_FUNCTION_DEFINITION=advisoryCalculationJob (infra/lambda.tf, T006/T057).
    // A função Lambda da API (SPRING_CLOUD_FUNCTION_DEFINITION=apiHandler) não tem bean
    // correspondente aqui de propósito — os controllers REST (T047-T051) rodam via
    // Spring MVC/DispatcherServlet (validados via Tomcat embarcado + MockMvc), não via
    // FunctionInvoker. Ver nota em pom.xml sobre o adaptador HTTP da Lambda de API
    // ainda precisar de revisão (aws-serverless-java-container-springboot3) antes do
    // deploy real — item em aberto, não resolvido nesta fase.
    @Bean
    public Runnable advisoryCalculationJob(AdvisoryCalculationJob job) {
        return job::run;
    }

    // SPRING_CLOUD_FUNCTION_DEFINITION=bookingEventsConsumer — Lambda acionada pela fila
    // SQS booking_events (infra/sqs.tf T006b). T059b hoje; T059 (booking.transferred/
    // booking.cancelled) usa a mesma function quando for desbloqueada.
    @Bean
    public Consumer<SQSEvent> bookingEventsConsumer(BookingEventsConsumer consumer) {
        return consumer::handle;
    }
}
