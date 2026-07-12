package com.empresa.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.empresa.booking.support.AbstractDynamoDbIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

/**
 * Integration test: hold expirado é ignorado no cálculo de vagas mesmo antes
 * do TTL nativo do DynamoDB apagar o item fisicamente (que só garante isso em
 * até 48h — inadequado para um hold de 10 minutos). A aplicação sempre ignora
 * holds com `expiresAt` vencido na leitura, independente do item ainda
 * existir; o job sweeper (T046, a cada 1 min) é reforço de higiene que
 * reconcilia o contador `held`, não a fonte de verdade de consistência (ver
 * plan.md, "Ponto técnico importante").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T019_ExpiredHoldIgnoredIntegrationTest extends AbstractDynamoDbIntegrationTest {

    private static final String VESSEL_ID = "vessel-expired-hold";
    private static final String DATE = "2026-12-05";
    private static final String TOUR_TYPE = "orla";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void novoHoldDeveSucederIgnorandoHoldExpiradoAindaNaoVarridoFisicamente() throws Exception {
        // limite=1, held=1 (pelo hold expirado abaixo) — nominalmente zero vagas,
        // mas o hold já venceu, então a leitura deve ignorá-lo.
        seedSeatCount(1, 0, 1);
        seedExpiredHold("hold-vencido", Instant.now().minus(5, ChronoUnit.MINUTES));

        var request = new CreateHoldRequest("buyer-novo", VESSEL_ID, DATE, TOUR_TYPE, 1);

        MvcResult result = mockMvc.perform(post("/bookings/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("hold expirado não deve contar como vaga ocupada, mesmo com o item ainda existindo fisicamente")
                .isEqualTo(201);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sweeperDeveDecrementarHeldERemoverHoldExpiradoEmAteUmMinuto() {
        seedSeatCount(5, 0, 5);
        seedExpiredHold("hold-para-varrer", Instant.now().minus(15, ChronoUnit.MINUTES));

        Object bean = applicationContext.containsBean("releaseExpiredHoldsJob")
                ? applicationContext.getBean("releaseExpiredHoldsJob")
                : null;
        assertThat(bean)
                .as("bean releaseExpiredHoldsJob (T046, sweeper) ainda não existe — implementado na Fase 3.3")
                .isNotNull();

        ((Runnable) bean).run();

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", s("VESSEL#" + VESSEL_ID));
        key.put("SK", s("SEATCOUNT#" + DATE + "#" + TOUR_TYPE));
        Map<String, AttributeValue> seatCount = DYNAMO_DB_CLIENT.getItem(GetItemRequest.builder()
                        .tableName(TABLE_NAME).key(key).build())
                .item();

        assertThat(seatCount.get("held").n()).isEqualTo("0");

        Map<String, AttributeValue> holdKey = new HashMap<>();
        holdKey.put("PK", s("HOLD#hold-para-varrer"));
        holdKey.put("SK", s("METADATA"));
        Map<String, AttributeValue> holdItem = DYNAMO_DB_CLIENT.getItem(GetItemRequest.builder()
                        .tableName(TABLE_NAME).key(holdKey).build())
                .item();

        assertThat(holdItem).as("HOLD deve ser removido pelo sweeper").isEmpty();
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

    private void seedExpiredHold(String holdId, Instant expiresAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("HOLD#" + holdId));
        item.put("SK", s("METADATA"));
        item.put("id", s(holdId));
        item.put("vesselId", s(VESSEL_ID));
        item.put("data", s(DATE));
        item.put("tipoPasseio", s(TOUR_TYPE));
        item.put("quantidade", n(1));
        item.put("expiresAt", s(expiresAt.toString()));
        item.put("ttl", n(expiresAt.getEpochSecond()));
        putItem(item);
    }

    private record CreateHoldRequest(
            String buyerId, String vesselId, String data, String tipoPasseio, int quantidade) {
    }
}
