package com.empresa.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Integration test: redução do `PlatformSeatLimit` (evento
 * `vessel.seatlimit.changed`) nunca deixa vagas restantes negativas nem afeta
 * reservas já confirmadas (FR-013, Opção C — mesma decisão já implementada
 * do lado vessel-management). A edição é sempre aceita sem bloqueio; o efeito
 * prático é só impedir novas vendas até o número voltar a ficar positivo.
 */
@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T025_SeatLimitReductionIntegrationTest extends AbstractDynamoDbIntegrationTest {

    private static final String VESSEL_ID = "vessel-seatlimit";
    private static final String DATE = "2026-12-15";
    private static final String TOUR_TYPE = "alto_mar";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void reducaoAbaixoDoVendidoNuncaDeixaVagasRestantesNegativasNemAfetaReservaConfirmada() throws Exception {
        seedSeatCount(10, 8, 0);
        seedConfirmedBooking("booking-seatlimit-1");

        operatorEventsConsumer().accept(sqsEventFor("vessel.seatlimit.changed", """
                {"vesselId":"%s","data":"%s","tipoPasseio":"%s","limite":3}
                """.formatted(VESSEL_ID, DATE, TOUR_TYPE)));

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", s("VESSEL#" + VESSEL_ID));
        key.put("SK", s("SEATCOUNT#" + DATE + "#" + TOUR_TYPE));
        Map<String, AttributeValue> seatCount = DYNAMO_DB_CLIENT.getItem(GetItemRequest.builder()
                        .tableName(TABLE_NAME).key(key).build())
                .item();

        assertThat(seatCount.get("limite").n()).isEqualTo("3");
        assertThat(seatCount.get("sold").n())
                .as("reservas já vendidas não são invalidadas pela redução")
                .isEqualTo("8");

        mockMvc.perform(get("/vessels/" + VESSEL_ID + "/calendar")
                        .param("from", DATE)
                        .param("to", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[0]." + TOUR_TYPE + ".vagasRestantes")
                        .value(0)); // max(0, 3 - 8 - 0), nunca negativo

        mockMvc.perform(get("/bookings/booking-seatlimit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmada"));
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

    private void seedSeatCount(int limite, int sold, int held) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("VESSEL#" + VESSEL_ID));
        item.put("SK", s("SEATCOUNT#" + DATE + "#" + TOUR_TYPE));
        item.put("limite", n(limite));
        item.put("sold", n(sold));
        item.put("held", n(held));
        putItem(item);
    }

    private void seedConfirmedBooking(String id) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("BOOKING#" + id));
        item.put("SK", s("METADATA"));
        item.put("id", s(id));
        item.put("buyerId", s("buyer-1"));
        item.put("vesselId", s(VESSEL_ID));
        item.put("data", s(DATE));
        item.put("tipoPasseio", s(TOUR_TYPE));
        item.put("status", s("confirmada"));
        putItem(item);
    }
}
