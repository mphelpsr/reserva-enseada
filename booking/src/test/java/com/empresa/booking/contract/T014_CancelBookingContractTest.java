package com.empresa.booking.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * Contract test: POST /bookings/{id}/cancel (FR-006/FR-007).
 *
 * Modelo binário de cancelamento por desistência do comprador — dentro da
 * janela (7 dias corridos da compra E até 48h antes do passeio) = reembolso
 * integral automático; fora dela = recusado, sem escalonamento. O detalhe dos
 * dois limites combinados (compra recente x passeio próximo) é do teste de
 * integração T021; aqui só o formato dos dois desfechos possíveis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T014_CancelBookingContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveAceitarCancelamentoDentroDaJanelaEReembolsarIntegralmente() throws Exception {
        seedBooking("booking-dentro-janela", Instant.now(), LocalDate.now().plusDays(30));

        mockMvc.perform(post("/bookings/booking-dentro-janela/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reembolsada"))
                .andExpect(jsonPath("$.reembolsoIntegral").value(true));
    }

    @Test
    void deveRecusarCancelamentoForaDaJanela() throws Exception {
        seedBooking("booking-fora-janela", Instant.now().minus(10, ChronoUnit.DAYS), LocalDate.now().plusDays(30));

        mockMvc.perform(post("/bookings/booking-fora-janela/cancel"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("CANCELLATION_WINDOW_EXPIRED"));
    }

    private void seedBooking(String id, Instant compradaEm, LocalDate dataPasseio) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("BOOKING#" + id));
        item.put("SK", s("METADATA"));
        item.put("id", s(id));
        item.put("buyerId", s("buyer-1"));
        item.put("vesselId", s("vessel-1"));
        item.put("status", s("confirmada"));
        item.put("compradaEm", s(compradaEm.toString()));
        item.put("data", s(dataPasseio.toString()));
        putItem(item);
    }
}
