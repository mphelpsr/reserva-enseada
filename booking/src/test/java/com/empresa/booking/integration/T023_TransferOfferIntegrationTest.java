package com.empresa.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

/**
 * Integration test: consumo de `vessel.transfer.viable` (FR-009, cenário 7).
 * Notifica o comprador com as novas condições e aguarda confirmação
 * explícita por até 48h (`POST /bookings/{id}/respond-transfer`, T015); sem
 * resposta no prazo, cancela automaticamente com reembolso integral.
 *
 * O payload consumido carrega `id` (correlação — "Contrato da Saga",
 * plan-vessel-management.md) e `motivo`, além de vesselId/data/tipoPasseio/
 * targetVesselId — precisam ser persistidos no booking para T015 conseguir
 * responder depois e para o eventual auto-cancelamento saber a que se refere.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T023_TransferOfferIntegrationTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void deveNotificarCompradorEAguardarConfirmacaoPorAteQuarentaEOitoHoras() {
        seedConfirmedBooking("booking-transfer-1", "vessel-1", "2026-12-20", "alto_mar");

        operatorEventsConsumer().accept(sqsEventFor("vessel.transfer.viable", """
                {"id":"transfer-attempt-1","vesselId":"vessel-1","data":"2026-12-20","tipoPasseio":"alto_mar","targetVesselId":"vessel-2","motivo":"avaria no motor"}
                """));

        try {
            mockMvc.perform(get("/bookings/booking-transfer-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("aguardando_transferencia"))
                    .andExpect(jsonPath("$.targetVesselId").value("vessel-2"))
                    .andExpect(jsonPath("$.transferAttemptId").value("transfer-attempt-1"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void deveCancelarAutomaticamenteComReembolsoIntegralSePassarQuarentaEOitoHorasSemResposta() {
        // Mesmo mecanismo periódico do sweeper de holds (T046, roda a cada 1 min) —
        // reutilizado aqui para reconciliar ofertas de transferência vencidas
        // (mesma cadência, mesma responsabilidade de "arrumar a casa" que o TTL
        // nativo do DynamoDB sozinho não garante a tempo). Interpretação a
        // confirmar na Fase 3.3 — tasks.md não nomeia um job dedicado para isso.
        seedBookingAwaitingTransfer(
                "booking-transfer-expirado", "vessel-2", Instant.now().minus(1, ChronoUnit.HOURS));

        Object sweeper = applicationContext.containsBean("releaseExpiredHoldsJob")
                ? applicationContext.getBean("releaseExpiredHoldsJob")
                : null;
        assertThat(sweeper)
                .as("bean releaseExpiredHoldsJob (T046) ainda não existe — implementado na Fase 3.3")
                .isNotNull();

        ((Runnable) sweeper).run();

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", s("BOOKING#booking-transfer-expirado"));
        key.put("SK", s("METADATA"));
        Map<String, AttributeValue> booking = DYNAMO_DB_CLIENT.getItem(GetItemRequest.builder()
                        .tableName(TABLE_NAME).key(key).build())
                .item();

        assertThat(booking.get("status").s()).isEqualTo("reembolsada");
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
        String escaped = messagePayloadJson.replace("\n", "").trim().replace("\"", "\\\"");
        String envelope = """
                {
                  "Message": "%s",
                  "MessageAttributes": {
                    "event-type": { "Type": "String", "Value": "%s" }
                  }
                }
                """.formatted(escaped, eventType);

        SQSEvent event = new SQSEvent();
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId("msg-" + System.nanoTime());
        message.setBody(envelope);
        event.setRecords(List.of(message));
        return event;
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

    private void seedBookingAwaitingTransfer(String id, String targetVesselId, Instant expiresAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("BOOKING#" + id));
        item.put("SK", s("METADATA"));
        item.put("id", s(id));
        item.put("buyerId", s("buyer-1"));
        item.put("vesselId", s("vessel-1"));
        item.put("status", s("aguardando_transferencia"));
        item.put("targetVesselId", s(targetVesselId));
        item.put("transferAttemptId", s("transfer-attempt-expirado"));
        item.put("transferOfferExpiresAt", s(expiresAt.toString()));
        putItem(item);
    }
}
