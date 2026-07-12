package com.empresa.booking.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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

import com.empresa.booking.support.AbstractDynamoDbIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Integration test: a confirmação de reserva aplica o split de pagamento no
 * Pagar.me — 12% para a plataforma, 88% para o proprietário (FR-015),
 * percentual configurável (`app.payment.platform-commission-percentage`,
 * application.yml), nunca hardcoded.
 *
 * Stub via WireMock (T056, `PagarmeClient`) — não chama o gateway real em
 * teste. O foco aqui é a composição financeira (comissão/líquido), não o
 * formato exato do payload do Pagar.me em si.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T026_PaymentSplitIntegrationTest extends AbstractDynamoDbIntegrationTest {

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
    void deveAplicarSplitDeDozePorCentoParaPlataformaEOitentaEOitoPorCentoParaProprietario() throws Exception {
        pagarmeStub.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/core/v5/orders.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":"or_pagarme_123","status":"paid"}
                                """)));

        LocalDate data = LocalDate.now().plusDays(10);
        seedHold("hold-split-1", "vessel-1", data, 10000); // R$ 100,00 em centavos
        seedSeatCount("vessel-1", data, "alto_mar", 10, 0, 1);
        seedRecebedor("vessel-1");

        var request = new ConfirmBookingRequest("pagarme-tx-split");

        mockMvc.perform(post("/bookings/hold-split-1/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorPago").value(10000))
                .andExpect(jsonPath("$.valorComissao").value(1200))  // 12% de 10000
                .andExpect(jsonPath("$.valorLiquido").value(8800));  // 88% de 10000

        pagarmeStub.verify(1, postRequestedFor(urlPathMatching("/core/v5/orders.*")));
    }

    private void seedHold(String holdId, String vesselId, LocalDate data, int valorTotalCentavos) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("HOLD#" + holdId));
        item.put("SK", s("METADATA"));
        item.put("id", s(holdId));
        item.put("buyerId", s("buyer-1"));
        item.put("vesselId", s(vesselId));
        item.put("data", s(data.toString()));
        item.put("tipoPasseio", s("alto_mar"));
        item.put("quantidade", n(1));
        item.put("valorTotalCentavos", n(valorTotalCentavos));
        putItem(item);
    }

    private void seedSeatCount(String vesselId, LocalDate data, String tipoPasseio, int limite, int sold, int held) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("VESSEL#" + vesselId));
        item.put("SK", s("SEATCOUNT#" + data + "#" + tipoPasseio));
        item.put("limite", n(limite));
        item.put("sold", n(sold));
        item.put("held", n(held));
        item.put("vagasDisponiveis", n(limite - sold - held));
        putItem(item);
    }

    private void seedRecebedor(String vesselId) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("VESSEL#" + vesselId));
        item.put("SK", s("RECEBEDOR"));
        item.put("vesselId", s(vesselId));
        item.put("pixKey", s("rec-1"));
        putItem(item);
    }

    private record ConfirmBookingRequest(String paymentReference) {
    }
}
