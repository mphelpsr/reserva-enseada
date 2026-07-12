package com.empresa.booking.contract;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import com.empresa.booking.support.AbstractDynamoDbIntegrationTest;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Contract test: POST /bookings/{id}/respond-transfer (FR-009).
 *
 * Resposta do comprador a uma oferta de transferência pendente (consumida de
 * `vessel.transfer.viable`, T023 cobre o consumo em si) — aceitar move a
 * reserva para a nova embarcação/dia; recusar dispara reembolso integral
 * (mesmo efeito de FR-006, sem checar a janela — a recusa aqui é sempre
 * aceita, é o proprietário que iniciou a mudança, não o comprador).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T015_RespondToTransferContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveAceitarTransferenciaEMoverAReserva() throws Exception {
        seedBookingAwaitingTransfer("booking-aguardando-1", "vessel-2");

        mockMvc.perform(post("/bookings/booking-aguardando-1/respond-transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RespondTransferRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("transferida"))
                .andExpect(jsonPath("$.vesselId").value("vessel-2"));
    }

    @Test
    void deveRecusarTransferenciaEReembolsarIntegralmente() throws Exception {
        seedBookingAwaitingTransfer("booking-aguardando-2", "vessel-2");

        mockMvc.perform(post("/bookings/booking-aguardando-2/respond-transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RespondTransferRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reembolsada"));
    }

    private void seedBookingAwaitingTransfer(String id, String targetVesselId) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("BOOKING#" + id));
        item.put("SK", s("METADATA"));
        item.put("id", s(id));
        item.put("buyerId", s("buyer-1"));
        item.put("vesselId", s("vessel-1"));
        item.put("status", s("aguardando_transferencia"));
        item.put("data", s(LocalDate.now().plusDays(30).toString()));
        item.put("tipoPasseio", s("alto_mar"));
        item.put("quantidade", n(1));
        item.put("targetVesselId", s(targetVesselId));
        item.put("transferAttemptId", s("transfer-attempt-1"));
        putItem(item);
    }

    private record RespondTransferRequest(boolean aceitar) {
    }
}
