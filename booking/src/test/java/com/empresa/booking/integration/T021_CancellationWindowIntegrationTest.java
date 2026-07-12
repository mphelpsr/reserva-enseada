package com.empresa.booking.integration;

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
 * Integration test: modelo binário de cancelamento por desistência do
 * comprador (FR-006/FR-007, cenários 4/5). As DUAS condições precisam valer
 * ao mesmo tempo para o reembolso integral automático ser concedido:
 * (a) até 7 dias corridos da compra, E (b) até 48h antes do passeio. Falhar
 * qualquer uma das duas recusa o cancelamento — sem reembolso parcial.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T021_CancellationWindowIntegrationTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveReembolsarIntegralmenteQuandoDentroDosDoisLimites() throws Exception {
        // comprada há 2 dias (dentro dos 7 dias) e passeio em 10 dias (bem além das 48h)
        seedBooking("booking-ok", Instant.now().minus(2, ChronoUnit.DAYS), LocalDate.now().plusDays(10));

        mockMvc.perform(post("/bookings/booking-ok/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reembolsada"))
                .andExpect(jsonPath("$.reembolsoIntegral").value(true));
    }

    @Test
    void deveRecusarQuandoComprouHaMaisDeSeteDias() throws Exception {
        // fora do prazo de arrependimento, mesmo com o passeio ainda distante
        seedBooking("booking-compra-antiga", Instant.now().minus(10, ChronoUnit.DAYS), LocalDate.now().plusDays(30));

        mockMvc.perform(post("/bookings/booking-compra-antiga/cancel"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("CANCELLATION_WINDOW_EXPIRED"));
    }

    @Test
    void deveRecusarQuandoPasseioEstaAMenosDeQuarentaEOitoHorasMesmoComCompraRecente() throws Exception {
        // comprada há 2 dias (dentro dos 7 dias), mas o passeio é amanhã — janela
        // comprimida pra 48h antes do passeio, que já não é mais respeitada
        seedBooking("booking-passeio-proximo", Instant.now().minus(2, ChronoUnit.DAYS), LocalDate.now().plusDays(1));

        mockMvc.perform(post("/bookings/booking-passeio-proximo/cancel"))
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
        item.put("tipoPasseio", s("alto_mar"));
        item.put("quantidade", n(1));
        putItem(item);
    }
}
