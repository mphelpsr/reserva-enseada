package com.empresa.booking.infrastructure.payment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * T039/T056. Cliente do Pagar.me (Stone) — cria a transação já com o split
 * de comissão embutido (FR-015): a plataforma e o proprietário recebem cada
 * um sua parte na MESMA transação, nunca um repasse manual posterior.
 *
 * **Desatualizado em relação ao modelo de pagamento vigente (revisão de
 * 2026-07-12, ver FR-015 em spec.md)**: o repasse ao proprietário passou a
 * ser split instantâneo via Pix (provedor tipo Transfeera/OpenPix), não mais
 * split nativo do Pagar.me por `recipient_id`. Este cliente continua
 * chamando o endpoint de split do Pagar.me (`/core/v5/orders`) como estava —
 * `pixKey` é repassado aqui só para preservar o contrato de chamada de
 * `ConfirmBookingUseCase`, mas a integração real com o provedor de split Pix
 * NÃO está implementada nesta classe (não há contrato de API definido em
 * nenhuma spec/plan para isso). Cartão continua sendo capturado via Pagar.me
 * normalmente — só o mecanismo de repasse ao proprietário precisa trocar
 * quando essa integração for desenhada.
 *
 * `pixKey` é responsabilidade de quem chama (`ConfirmBookingUseCase`) — este
 * cliente não sabe de onde ele vem; ver `VesselRecebedorRepository` e a nota
 * sobre `vessel.recebedor.changed` ("Contrato da Saga" em plan.md) para o
 * racional completo.
 */
@Component
public class PagarmeClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;

    public PagarmeClient(
            ObjectMapper objectMapper,
            @Value("${app.payment.pagarme-api-key:}") String apiKey,
            @Value("${app.payment.pagarme-base-url}") String baseUrl) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    /**
     * Cria a ordem com split: `pixKey` recebe `valorLiquidoCentavos`, a
     * plataforma retém `valorComissaoCentavos` — nunca lança em caso de
     * pagamento recusado pelo gateway em si (o chamador decide o que fazer
     * com um status != "paid"), só em falha de infraestrutura/rede.
     */
    public PagarmeOrderResult createOrderWithSplit(
            String paymentReference, String pixKey, long valorTotalCentavos, long valorComissaoCentavos, long valorLiquidoCentavos) {
        try {
            Map<String, Object> payload = Map.of(
                    "payment_reference", paymentReference,
                    "amount", valorTotalCentavos,
                    "split", List.of(
                            Map.of("recipient_id", pixKey, "amount", valorLiquidoCentavos, "type", "flat"),
                            Map.of("recipient_id", "platform", "amount", valorComissaoCentavos, "type", "flat")));

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/core/v5/orders"))
                    .header("Authorization", "Basic " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new PaymentFailedException("Pagar.me respondeu " + response.statusCode() + ": " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            return new PagarmeOrderResult(root.path("id").asText(), root.path("status").asText());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PaymentFailedException("Falha ao criar ordem no Pagar.me", e);
        }
    }
}
