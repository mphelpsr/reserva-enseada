package com.empresa.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.empresa.booking.support.AbstractDynamoDbIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * T062 — teste de carga simulando pico de alta temporada sobre `POST
 * /bookings/hold` (FR-003), pra ajudar a decidir se *provisioned
 * concurrency* (T010, `var.enable_provisioned_concurrency`) é necessária.
 *
 * NÃO substitui T018 (garantia de correção sob concorrência real na ÚLTIMA
 * vaga) — aqui cada requisição mira um dia/embarcação/tipo de passeio
 * diferente, sem disputa de vaga entre si, porque o objetivo é medir
 * throughput/latência do handler sob carga, não a `ConditionExpression`.
 *
 * Roda dentro do mesmo contexto Spring/DynamoDB Local usado nos demais
 * testes de integração (não é uma Lambda real nem mede cold start em AWS —
 * só o tempo de processamento do handler já quente, incluindo a
 * `TransactWriteItems` real contra DynamoDB Local). Excluído da descoberta
 * automática do Surefire (nome não termina em `Test`/`Tests`/`TestCase`) por
 * ser mais pesado que os demais testes de integração — rodar sob demanda
 * com `mvn test -Dtest=T062_HoldEndpointLoadTest`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T062_HoldEndpointLoadTest extends AbstractDynamoDbIntegrationTest {

    private static final int PICO_DE_ALTA_TEMPORADA = 50;
    private static final String VESSEL_ID = "vessel-load";
    private static final String DATE = "2026-12-24";
    private static final String TOUR_TYPE = "orla";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void suportaPicoDeHoldsSimultaneosSemErroDeServidorNemLatenciaExcessiva() throws Exception {
        for (int i = 0; i < PICO_DE_ALTA_TEMPORADA; i++) {
            seedSeatCountWithOneRemainingSeat(i);
        }

        ExecutorService executor = Executors.newFixedThreadPool(PICO_DE_ALTA_TEMPORADA);
        CountDownLatch readyLatch = new CountDownLatch(PICO_DE_ALTA_TEMPORADA);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<Callable<Long>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < PICO_DE_ALTA_TEMPORADA; i++) {
                tasks.add(holdTask("buyer-" + i, i, readyLatch, startLatch));
            }

            List<Future<Long>> futures = tasks.stream().map(executor::submit).toList();

            readyLatch.await(5, TimeUnit.SECONDS);
            long inicio = System.nanoTime();
            startLatch.countDown();

            List<Long> latenciasMs = futures.stream()
                    .map(f -> {
                        try {
                            return f.get(30, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .sorted()
                    .toList();
            long duracaoTotalMs = (System.nanoTime() - inicio) / 1_000_000;

            long p95 = latenciasMs.get((int) (latenciasMs.size() * 0.95) - 1);
            System.out.printf(
                    "T062 load test: %d holds concorrentes em %dms (p95=%dms, max=%dms)%n",
                    PICO_DE_ALTA_TEMPORADA, duracaoTotalMs, p95, latenciasMs.get(latenciasMs.size() - 1));

            // Limiar frouxo de propósito: o objetivo aqui é detectar degradação
            // grosseira (ex.: contenção de conexão, lock indevido), não fazer
            // benchmarking preciso — para decidir sobre provisioned concurrency
            // (cold start em AWS Lambda real), este número não é suficiente
            // sozinho, precisa ser correlacionado com métricas de produção/CloudWatch.
            assertThat(p95)
                    .as("p95 de latência sob pico simulado não deve degradar grosseiramente")
                    .isLessThan(5000);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Long> holdTask(String buyerId, int seatIndex, CountDownLatch readyLatch, CountDownLatch startLatch) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            var request = new CreateHoldRequest(buyerId, VESSEL_ID + "-" + seatIndex, DATE, TOUR_TYPE, 1);

            long inicio = System.nanoTime();
            var result = mockMvc.perform(post("/bookings/hold")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();
            long latenciaMs = (System.nanoTime() - inicio) / 1_000_000;

            assertThat(result.getResponse().getStatus())
                    .as("hold %d não pode falhar por erro de servidor sob carga", seatIndex)
                    .isEqualTo(201);
            return latenciaMs;
        };
    }

    private void seedSeatCountWithOneRemainingSeat(int seatIndex) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("VESSEL#" + VESSEL_ID + "-" + seatIndex));
        item.put("SK", s("SEATCOUNT#" + DATE + "#" + TOUR_TYPE));
        item.put("limite", n(1));
        item.put("sold", n(0));
        item.put("held", n(0));
        item.put("vagasDisponiveis", n(1));
        putItem(item);
    }

    private record CreateHoldRequest(
            String buyerId, String vesselId, String data, String tipoPasseio, int quantidade) {
    }
}
