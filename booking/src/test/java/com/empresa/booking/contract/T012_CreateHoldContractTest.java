package com.empresa.booking.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.empresa.booking.support.AbstractDynamoDbIntegrationTest;

/**
 * Contract test: POST /bookings/hold (FR-003, FR-004).
 *
 * Retém vagas por até 10 minutos com escrita condicional atômica — o teste de
 * concorrência dedicado (T018) cobre a garantia de não-overselling; aqui só o
 * formato: sucesso (holdId + expiresAt) e recusa por vagas insuficientes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T012_CreateHoldContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveCriarHoldComExpiracaoDeDezMinutos() throws Exception {
        var request = new CreateHoldRequest("buyer-1", "vessel-1", "2026-08-10", "orla", 2);

        mockMvc.perform(post("/bookings/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdId").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.vesselId").value("vessel-1"))
                .andExpect(jsonPath("$.quantidade").value(2));
    }

    @Test
    void deveRecusarQuandoQuantidadeExcedeVagasRestantes() throws Exception {
        var request = new CreateHoldRequest("buyer-1", "vessel-1", "2026-08-10", "orla", 9999);

        mockMvc.perform(post("/bookings/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_SEATS"));
    }

    private record CreateHoldRequest(
            String buyerId, String vesselId, String data, String tipoPasseio, int quantidade) {
    }
}
