package com.empresa.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.empresa.booking.support.AbstractDynamoDbIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Integration test: prazo mínimo de 24h de antecedência para compra (FR-014,
 * cenário 9). Benchmark: operadores marítimos brasileiros trabalham com
 * janelas de 24-48h — 24h escolhido como o mínimo da spec. Vagas restantes
 * sendo positivas não é suficiente: o dia precisa também estar a pelo menos
 * 24h de distância do agora.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T020_MinimumAdvancePurchaseIntegrationTest extends AbstractDynamoDbIntegrationTest {

    private static final String VESSEL_ID = "vessel-antecedencia";
    private static final String TOUR_TYPE = "orla";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveRecusarCompraParaPasseioComMenosDeVinteEQuatroHorasDeAntecedencia() throws Exception {
        String hoje = LocalDate.now().toString();
        seedSeatCount(hoje, 10, 0, 0);

        var request = new CreateHoldRequest("buyer-1", VESSEL_ID, hoje, TOUR_TYPE, 1);

        mockMvc.perform(post("/bookings/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MINIMUM_ADVANCE_PURCHASE_NOT_MET"));
    }

    @Test
    void devePermitirCompraComAntecedenciaSuficiente() throws Exception {
        String emTresDias = LocalDate.now().plusDays(3).toString();
        seedSeatCount(emTresDias, 10, 0, 0);

        var request = new CreateHoldRequest("buyer-1", VESSEL_ID, emTresDias, TOUR_TYPE, 1);

        mockMvc.perform(post("/bookings/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void seedSeatCount(String data, int limite, int sold, int held) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("VESSEL#" + VESSEL_ID));
        item.put("SK", s("SEATCOUNT#" + data + "#" + TOUR_TYPE));
        item.put("limite", n(limite));
        item.put("sold", n(sold));
        item.put("held", n(held));
        putItem(item);
    }

    private record CreateHoldRequest(
            String buyerId, String vesselId, String data, String tipoPasseio, int quantidade) {
    }
}
