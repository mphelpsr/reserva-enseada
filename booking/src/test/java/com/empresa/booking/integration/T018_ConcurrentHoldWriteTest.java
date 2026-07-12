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
import org.springframework.test.web.servlet.MvcResult;

import com.empresa.booking.support.AbstractDynamoDbIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Teste de concorrência (FR-003, cenário 3): dois compradores disputando a
 * ÚLTIMA vaga restante do mesmo dia/embarcação. A escrita precisa usar
 * `TransactWriteItems` com `ConditionExpression` (Princípio II) — o resultado
 * observável aqui é que as duas requisições completam (uma com sucesso, outra
 * com erro imediato de vagas insuficientes), nunca as duas com sucesso
 * (overselling).
 *
 * Hoje (Fase 3.2) o endpoint não existe, então isso falha antes mesmo de
 * testar concorrência de fato — passa a validar a garantia real assim que
 * CreateHoldUseCase (T038) e SeatCountRepository (T035) existirem.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T018_ConcurrentHoldWriteTest extends AbstractDynamoDbIntegrationTest {

    private static final String VESSEL_ID = "vessel-concurrency";
    private static final String DATE = "2026-12-01";
    private static final String TOUR_TYPE = "orla";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void apenasUmDosDoisHoldsSimultaneosParaAUltimaVagaDeveSuceder() throws Exception {
        seedSeatCountWithOneRemainingSeat();

        int concurrentHolds = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentHolds);
        CountDownLatch readyLatch = new CountDownLatch(concurrentHolds);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<Callable<MvcResult>> tasks = List.of(
                    holdTask("buyer-a", readyLatch, startLatch),
                    holdTask("buyer-b", readyLatch, startLatch));

            List<Future<MvcResult>> futures = tasks.stream().map(executor::submit).toList();

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            List<Integer> statuses = futures.stream()
                    .map(f -> {
                        try {
                            return f.get(10, TimeUnit.SECONDS).getResponse().getStatus();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();

            long sucessos = statuses.stream().filter(s -> s == 201).count();
            long recusados = statuses.stream().filter(s -> s == 409).count();

            assertThat(sucessos)
                    .as("exatamente um dos dois holds deve suceder — a última vaga não pode ir para os dois")
                    .isEqualTo(1);
            assertThat(recusados)
                    .as("o outro hold deve ser recusado por vagas insuficientes, não falhar por erro de servidor")
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<MvcResult> holdTask(String buyerId, CountDownLatch readyLatch, CountDownLatch startLatch) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            var request = new CreateHoldRequest(buyerId, VESSEL_ID, DATE, TOUR_TYPE, 1);
            return mockMvc.perform(post("/bookings/hold")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();
        };
    }

    private void seedSeatCountWithOneRemainingSeat() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("VESSEL#" + VESSEL_ID));
        item.put("SK", s("SEATCOUNT#" + DATE + "#" + TOUR_TYPE));
        item.put("limite", n(1));
        item.put("sold", n(0));
        item.put("held", n(0));
        putItem(item);
    }

    private record CreateHoldRequest(
            String buyerId, String vesselId, String data, String tipoPasseio, int quantidade) {
    }
}
