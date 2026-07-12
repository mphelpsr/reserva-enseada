package com.empresa.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.empresa.booking.support.AbstractDynamoDbIntegrationTest;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test: consumo de `vessel.cancellation.operator-initiated`
 * dispara reembolso integral automático com o motivo REAL comunicado ao
 * comprador (FR-008, cenário 6) — nunca uma mensagem genérica (Princípio VII).
 *
 * Consumido via o mesmo mecanismo Lambda/SQS que vai atender T049-T052 (fila
 * `operator_events`, infra/sqs.tf) — aqui invocado diretamente pelo bean
 * `operatorEventsConsumer` (roteamento por `event-type`, mesma convenção do
 * lado vessel-management), sem precisar de um SQS real via LocalStack.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T022_OperatorCancellationIntegrationTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void deveReembolsarComMotivoRealAoConsumirCancelamentoDoOperador() {
        seedConfirmedBooking("booking-op-cancel", "vessel-1", "2026-12-20", "alto_mar");

        Consumer<SQSEvent> consumer = operatorEventsConsumer();

        String motivoReal = "avaria no motor detectada na vistoria pré-saída";
        consumer.accept(sqsEventFor("vessel.cancellation.operator-initiated", """
                {"vesselId":"vessel-1","data":"2026-12-20","tipoPasseio":"alto_mar","motivo":"%s"}
                """.formatted(motivoReal)));

        assertBookingStatusAndMotivo("booking-op-cancel", "reembolsada", motivoReal);
    }

    private Consumer<SQSEvent> operatorEventsConsumer() {
        Object bean = applicationContext.containsBean("operatorEventsConsumer")
                ? applicationContext.getBean("operatorEventsConsumer")
                : null;
        assertThat(bean)
                .as("bean operatorEventsConsumer (T049-T052) ainda não existe — implementado na Fase 3.4")
                .isNotNull();
        return (Consumer<SQSEvent>) bean;
    }

    private SQSEvent sqsEventFor(String eventType, String messagePayloadJson) {
        String envelope = """
                {
                  "Message": %s,
                  "MessageAttributes": {
                    "event-type": { "Type": "String", "Value": "%s" }
                  }
                }
                """.formatted(toJsonString(messagePayloadJson), eventType);

        SQSEvent event = new SQSEvent();
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg-" + System.nanoTime());
        message.setBody(envelope);
        event.setRecords(List.of(message));
        return event;
    }

    /** Serializa o payload de negócio como string JSON escapada dentro do envelope SNS. */
    private String toJsonString(String rawJson) {
        return "\"" + rawJson.replace("\"", "\\\"").replace("\n", "").trim() + "\"";
    }

    private void assertBookingStatusAndMotivo(String bookingId, String status, String motivo) {
        try {
            mockMvc.perform(get("/bookings/" + bookingId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(status))
                    .andExpect(jsonPath("$.motivo").value(motivo));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void seedConfirmedBooking(String id, String vesselId, String data, String tipoPasseio) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("BOOKING#" + id));
        item.put("SK", s("METADATA"));
        item.put("id", s(id));
        item.put("buyerId", s("buyer-1"));
        item.put("vesselId", s(vesselId));
        item.put("data", s(data));
        item.put("tipoPasseio", s(tipoPasseio));
        item.put("status", s("confirmada"));
        putItem(item);
    }
}
