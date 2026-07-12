package com.empresa.vesselmanagement.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.empresa.vesselmanagement.support.AbstractDynamoDbIntegrationTest;

/**
 * Contract test: GET /vessels/{id}/calendar?from=&to= — visão consolidada usada
 * pelo painel desktop do proprietário (plan.md, Phase 1 — API).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T015_GetVesselCalendarContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveRetornarCalendarioConsolidadoDoIntervalo() throws Exception {
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
}
