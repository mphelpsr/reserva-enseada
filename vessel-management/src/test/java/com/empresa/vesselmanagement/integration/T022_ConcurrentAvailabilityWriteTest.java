package com.empresa.vesselmanagement.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.List;
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

import com.empresa.vesselmanagement.support.AbstractDynamoDbIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Teste de concorrência: duas escritas simultâneas de disponibilidade no mesmo
 * dia/embarcação não geram estado inconsistente. O plan.md determina que essas
 * escritas usem `ConditionExpression` do DynamoDB (Princípio II) — o resultado
 * observável aqui é que as duas requisições completam com sucesso e o estado
 * final é EXATAMENTE o de uma das duas (last-write-wins consistente), nunca um
 * valor corrompido/misturado.
 *
 * Hoje (Fase 3.2) o endpoint não existe, então isso falha antes mesmo de testar
 * concorrência de fato — o teste passa a validar a garantia real assim que
 * SetAvailabilityUseCase (T041) e AvailabilityRepository (T033) existirem.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T022_ConcurrentAvailabilityWriteTest extends AbstractDynamoDbIntegrationTest {

    private static final String VESSEL_ID = "vessel-concurrency";
    private static final String DATE = "2026-12-01";
    private static final String TOUR_TYPE = "orla";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void duasEscritasSimultaneasNaoDevemCorromperOEstadoFinal() throws Exception {
        int concurrentWrites = 2;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentWrites);
        CountDownLatch readyLatch = new CountDownLatch(concurrentWrites);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<Callable<MvcResult>> tasks = List.of(
                    writeTask("motivo-A", readyLatch, startLatch),
                    writeTask("motivo-B", readyLatch, startLatch));

            List<Future<MvcResult>> futures = tasks.stream().map(executor::submit).toList();

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            List<MvcResult> results = futures.stream()
                    .map(f -> {
                        try {
                            return f.get(10, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();

            for (MvcResult result : results) {
                assertThat(result.getResponse().getStatus())
                        .as("nenhuma das duas escritas concorrentes deve falhar por erro de servidor")
                        .isEqualTo(200);
            }

            MvcResult finalState = mockMvc.perform(get("/vessels/" + VESSEL_ID + "/calendar")
                            .param("from", DATE)
                            .param("to", DATE))
                    .andReturn();

            JsonNode calendar = objectMapper.readTree(finalState.getResponse().getContentAsString());
            String motivoFinal = calendar.at("/dias/0/" + TOUR_TYPE + "/motivo").asText();

            assertThat(motivoFinal)
                    .as("o estado final deve ser EXATAMENTE o de uma das duas escritas, nunca um valor corrompido")
                    .isIn("motivo-A", "motivo-B");
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<MvcResult> writeTask(String motivo, CountDownLatch readyLatch, CountDownLatch startLatch) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            return mockMvc.perform(put("/vessels/" + VESSEL_ID + "/availability/" + DATE + "/" + TOUR_TYPE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SetAvailabilityRequest(true, motivo))))
                    .andReturn();
        };
    }

    private record SetAvailabilityRequest(boolean disponivel, String motivo) {
    }
}
