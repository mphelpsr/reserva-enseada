package com.empresa.vesselmanagement.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.empresa.vesselmanagement.support.AbstractDynamoDbIntegrationTest;

/**
 * Contract test: PUT /vessels/{id}/seat-limit/{data}/{tipoPasseio} (FR-015).
 *
 * A lógica completa do contador de padrão automático (2x 10%, depois zero) é do
 * teste de integração T018; aqui só o formato: definição explícita de limite e a
 * validação de que o limite não pode exceder a capacidade máxima.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T014_SetSeatLimitContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveDefinirLimiteExplicitoDeVagas() throws Exception {
        var request = new SetSeatLimitRequest(10);

        mockMvc.perform(put("/vessels/vessel-1/seat-limit/2026-08-10/orla")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(10))
                .andExpect(jsonPath("$.origem").value("MANUAL"));
    }

    @Test
    void deveRecusarLimiteAcimaDaCapacidadeMaxima() throws Exception {
        var request = new SetSeatLimitRequest(9999);

        mockMvc.perform(put("/vessels/vessel-1/seat-limit/2026-08-10/orla")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private record SetSeatLimitRequest(Integer limite) {
    }
}
