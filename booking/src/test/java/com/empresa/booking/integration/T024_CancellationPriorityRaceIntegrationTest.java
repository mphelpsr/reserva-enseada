package com.empresa.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.time.LocalDate;
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
 * Integration test: corrida entre cancelamento do comprador e oferta de
 * transferência do proprietário — o cancelamento do comprador SEMPRE prevalece
 * (FR-009, alinhado ao precedente T4F/Taylor Swift). Se o comprador já pediu
 * cancelamento antes/durante a chegada da oferta, ela é descartada e o
 * reembolso integral segue normalmente — nunca reacomodação forçada.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T024_CancellationPriorityRaceIntegrationTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void ofertaDeTransferenciaDeveSerDescartadaQuandoCompradorJaCancelou() throws Exception {
        seedConfirmedBooking("booking-corrida", "vessel-1", "2026-12-20", "alto_mar");

        // 1) comprador cancela primeiro (dentro da janela, FR-006)
        mockMvc.perform(post("/bookings/booking-corrida/cancel"));

        // 2) oferta de transferência chega DEPOIS — deve ser descartada, não reabrir a reserva
        operatorEventsConsumer().accept(sqsEventFor("vessel.transfer.viable", """
                {"id":"transfer-attempt-corrida","vesselId":"vessel-1","data":"2026-12-20","tipoPasseio":"alto_mar","targetVesselId":"vessel-2","motivo":"avaria no motor"}
                """));

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", s("BOOKING#booking-corrida"));
        key.put("SK", s("METADATA"));
        Map<String, AttributeValue> booking = DYNAMO_DB_CLIENT.getItem(GetItemRequest.builder()
                        .tableName(TABLE_NAME).key(key).build())
                .item();

        assertThat(booking.get("status").s())
                .as("cancelamento do comprador sempre prevalece — a oferta de transferência não pode reabrir a reserva")
                .isEqualTo("reembolsada");
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
        item.put("compradaEm", s(Instant.now().toString()));
        item.put("dataPasseio", s(LocalDate.now().plusDays(30).toString()));
        putItem(item);
    }
}
