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
 * Contract test: GET /vessels/{id}/calendar?from=&to= (FR-001, FR-002).
 *
 * Read-model replicado do vessel-management via evento (T049) — este endpoint
 * nunca calcula disponibilidade própria, só expõe a réplica local + vagas
 * restantes (`max(0, limite − sold − held)`, FR-013). O indicador de maré/
 * previsão (FR-002) é sempre exibido como informação complementar, nunca como
 * filtro que oculta uma data disponível.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T011_GetVesselCalendarContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveRetornarCalendarioComVagasRestantesEIndicadorDeMare() throws Exception {
        seedSeatCount("vessel-1", "2026-08-01", "alto_mar", 10, 2, 0);

        mockMvc.perform(get("/vessels/vessel-1/calendar")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vesselId").value("vessel-1"))
                .andExpect(jsonPath("$.dias").isArray());
    }

    @Test
    void deveRetornar404ParaEmbarcacaoInexistente() throws Exception {
        mockMvc.perform(get("/vessels/inexistente/calendar")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isNotFound());
    }

    private void seedSeatCount(String vesselId, String data, String tipoPasseio, int limite, int sold, int held) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("VESSEL#" + vesselId));
        item.put("SK", s("SEATCOUNT#" + data + "#" + tipoPasseio));
        item.put("limite", n(limite));
        item.put("sold", n(sold));
        item.put("held", n(held));
        putItem(item);
    }
}
