package com.empresa.vesselmanagement.infrastructure.external;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.empresa.vesselmanagement.domain.advisory.AdvisoryCondition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * T056. Integração com a Stormglass (maré + previsão do tempo marítima num único
 * contrato — decisão de Fase 0 do plan.md). Consumido só pelo job assíncrono
 * (T057); nunca chamado do caminho síncrono da API (Princípio I — o advisory é
 * apoio, nunca pode atrasar/bloquear uma operação do proprietário).
 *
 * Os limiares de altura de onda/vento abaixo são um primeiro corte (heurística),
 * não um valor validado operacionalmente — plan.md não define os limiares exatos
 * de FAVORAVEL/DESFAVORAVEL; ajustar quando houver critério náutico formal.
 */
@Component
public class StormglassClient {

    private static final Logger log = LoggerFactory.getLogger(StormglassClient.class);
    private static final double WAVE_HEIGHT_THRESHOLD_METERS = 1.5;
    private static final double WIND_SPEED_THRESHOLD_MPS = 10.0;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public StormglassClient(ObjectMapper objectMapper, @Value("${app.stormglass.api-key:}") String apiKey) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    /** Vazio se a API não está configurada, falhar, ou não tiver dados pro dia pedido — nunca lança. */
    public Optional<AdvisoryCondition> fetchCondition(double latitude, double longitude, LocalDate data) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("STORMGLASS_API_KEY não configurada — advisory pulado para {}/{}", latitude, longitude);
            return Optional.empty();
        }

        Instant start = data.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = data.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        URI uri = URI.create("https://api.stormglass.io/v2/weather/point"
                + "?lat=" + latitude
                + "&lng=" + longitude
                + "&params=waveHeight,windSpeed"
                + "&start=" + start
                + "&end=" + end);

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Authorization", apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Stormglass respondeu {} para {}/{}/{}", response.statusCode(), latitude, longitude, data);
                return Optional.empty();
            }

            return Optional.of(parseCondition(response.body()));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Falha ao consultar Stormglass para {}/{}/{}", latitude, longitude, data, e);
            return Optional.empty();
        }
    }

    private AdvisoryCondition parseCondition(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode hours = root.path("hours");

        double maxWaveHeight = 0;
        double maxWindSpeed = 0;
        for (JsonNode hour : hours) {
            maxWaveHeight = Math.max(maxWaveHeight, hour.path("waveHeight").path("sg").asDouble(0));
            maxWindSpeed = Math.max(maxWindSpeed, hour.path("windSpeed").path("sg").asDouble(0));
        }

        boolean desfavoravel = maxWaveHeight > WAVE_HEIGHT_THRESHOLD_METERS || maxWindSpeed > WIND_SPEED_THRESHOLD_MPS;
        return desfavoravel ? AdvisoryCondition.DESFAVORAVEL : AdvisoryCondition.FAVORAVEL;
    }
}
