package com.empresa.booking.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import com.empresa.booking.support.AbstractDynamoDbIntegrationTest;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Contract test: POST /bookings/{holdId}/confirm (FR-005).
 *
 * Chamado após confirmação de pagamento no Pagar.me (split 12/88%, T026 cobre
 * o detalhe do split) — aqui só o formato: confirma a reserva e move
 * held→sold definitivamente. Sem hold correspondente, 404. Usa o mesmo stub
 * WireMock de T026 (T056, PagarmeClient) — sem ele chamaria o Pagar.me real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T013_ConfirmBookingContractTest extends AbstractDynamoDbIntegrationTest {

    private static WireMockServer pagarmeStub;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startPagarmeStub() {
        pagarmeStub = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        pagarmeStub.start();
    }

    @AfterAll
    static void stopPagarmeStub() {
        pagarmeStub.stop();
    }

    @DynamicPropertySource
    static void pagarmeProperties(DynamicPropertyRegistry registry) {
        registry.add("app.payment.pagarme-base-url", () -> "http://localhost:" + pagarmeStub.port());
    }

    @Test
    void deveConfirmarReservaAposPagamentoAprovado() throws Exception {
        pagarmeStub.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/core/v5/orders.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"or_pagarme_t013","status":"paid"}
                                """)));

        seedHold("hold-1", "vessel-1");
        seedRecebedor("vessel-1");

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

    private void seedHold(String holdId, String vesselId) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("HOLD#" + holdId));
        item.put("SK", s("METADATA"));
        item.put("id", s(holdId));
        item.put("buyerId", s("buyer-1"));
        item.put("vesselId", s(vesselId));
        item.put("data", s(LocalDate.now().plusDays(10).toString()));
        item.put("tipoPasseio", s("alto_mar"));
        item.put("quantidade", n(1));
        item.put("expiresAt", s(Instant.now().plus(5, ChronoUnit.MINUTES).toString()));
        item.put("valorTotalCentavos", n(15000));
        putItem(item);
    }

    private void seedRecebedor(String vesselId) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("VESSEL#" + vesselId));
        item.put("SK", s("RECEBEDOR"));
        item.put("vesselId", s(vesselId));
        item.put("recebedorId", s("rec-1"));
        putItem(item);
    }

    private record ConfirmBookingRequest(String paymentReference) {
    }
}
