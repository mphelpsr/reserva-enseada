package com.empresa.booking.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.empresa.booking.support.AbstractDynamoDbIntegrationTest;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Contract test: GET /bookings?buyerId= (FR-011).
 *
 * Histórico do comprador — usa GSI1 (GSI1PK=BUYER#id) para listar sem scan
 * (plan.md, access pattern 5). O autorizador Cognito (T058) resolve o
 * comprador autenticado em produção; nos testes o buyerId vem como query
 * param direto, mesma convenção usada em vessel-management para ownerId.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T016_ListBuyerBookingsContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveListarReservasDoComprador() throws Exception {
        seedBooking("booking-a", "buyer-t016", "confirmada");
        seedBooking("booking-b", "buyer-t016", "cancelada");

        mockMvc.perform(get("/bookings").param("buyerId", "buyer-t016"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void deveRetornarListaVaziaParaCompradorSemReservas() throws Exception {
        mockMvc.perform(get("/bookings").param("buyerId", "buyer-sem-reservas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private void seedBooking(String id, String buyerId, String status) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("BOOKING#" + id));
        item.put("SK", s("METADATA"));
        item.put("GSI1PK", s("BUYER#" + buyerId));
        item.put("GSI1SK", s("BOOKING#" + id));
        item.put("id", s(id));
        item.put("buyerId", s(buyerId));
        item.put("vesselId", s("vessel-1"));
        item.put("status", s(status));
        putItem(item);
    }
}
