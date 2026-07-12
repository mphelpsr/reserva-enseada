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
 * Contract test: POST /bookings/{holdId}/confirm (FR-005).
 *
 * Chamado após confirmação de pagamento no Pagar.me (split 12/88%, T026 cobre
 * o detalhe do split) — aqui só o formato: confirma a reserva e move
 * held→sold definitivamente. Sem hold correspondente, 404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T013_ConfirmBookingContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveConfirmarReservaAposPagamentoAprovado() throws Exception {
        var request = new ConfirmBookingRequest("pagarme-tx-123");

        mockMvc.perform(post("/bookings/hold-1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmada"))
                .andExpect(jsonPath("$.id").isString());
    }

    @Test
    void deveRetornar404ParaHoldInexistenteOuExpirado() throws Exception {
        var request = new ConfirmBookingRequest("pagarme-tx-999");

        mockMvc.perform(post("/bookings/hold-inexistente/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    private record ConfirmBookingRequest(String paymentReference) {
    }
}
