package com.empresa.vesselmanagement.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.empresa.vesselmanagement.support.AbstractDynamoDbIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Integration test: fluxo de cancelamento de dia com reserva confirmada — tenta
 * transferência na mesma frota primeiro; se não houver embarcação com vaga,
 * publica cancelamento com reembolso integral (FR-007, Princípio VII).
 *
 * Cenário A (spec.md, cenário 5 — caminho "transferência viável"): há outra
 * embarcação do MESMO proprietário com vaga disponível no dia -> a disponibilidade
 * original NÃO muda ainda (aguarda confirmação do comprador, fora deste módulo) e
 * o sistema responde 202 (decisão pendente).
 *
 * Cenário B (mesmo cenário 5, caminho "sem alternativa"): nenhuma embarcação da
 * frota tem vaga -> cancelamento com reembolso integral é disparado de imediato,
 * sem janela de análise; a disponibilidade muda na hora (200, decisão final).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T021_CancelDayWithBookingsFlowIntegrationTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void tentaTransferenciaNaMesmaFrotaQuandoHaVessselComVagaNoDia() throws Exception {
        String ownerId = "owner-frota-com-vaga";
        String vesselA = registerVessel(ownerId, "Vessel A");
        String vesselB = registerVessel(ownerId, "Vessel B");
        String data = "2026-12-24";
        String tipoPasseio = "alto_mar";

        seedConfirmedBookingCount(vesselA, data, tipoPasseio, 3);
        // Vessel B (mesma frota) tem vaga disponível no dia
        mockMvc.perform(put("/vessels/" + vesselB + "/availability/" + data + "/" + tipoPasseio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SetAvailabilityRequest(true, null))));
        mockMvc.perform(put("/vessels/" + vesselB + "/seat-limit/" + data + "/" + tipoPasseio)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SetSeatLimitRequest(5))));

        mockMvc.perform(put("/vessels/" + vesselA + "/availability/" + data + "/" + tipoPasseio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetAvailabilityRequest(false, "avaria no motor"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("TRANSFERENCIA_EM_ANDAMENTO"))
                .andExpect(jsonPath("$.embarcacaoAlternativaId").value(vesselB));

        // disponibilidade original NÃO muda ainda — aguarda confirmação do comprador
        mockMvc.perform(get("/vessels/" + vesselA + "/calendar")
                        .param("from", data)
                        .param("to", data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[0].alto_mar.disponivel").value(true));
    }

    @Test
    void publicaCancelamentoImediatoQuandoNaoHaVagaNaFrota() throws Exception {
        String ownerId = "owner-frota-sem-vaga";
        String vesselA = registerVessel(ownerId, "Vessel A2");
        String data = "2026-12-25";
        String tipoPasseio = "alto_mar";

        seedConfirmedBookingCount(vesselA, data, tipoPasseio, 3);
        // proprietário não tem nenhuma outra embarcação (frota de uma unidade só)

        mockMvc.perform(put("/vessels/" + vesselA + "/availability/" + data + "/" + tipoPasseio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetAvailabilityRequest(false, "força maior"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELAMENTO_INICIADO"))
                .andExpect(jsonPath("$.disponivel").value(false));

        // sem alternativa, a mudança é final e imediata
        mockMvc.perform(get("/vessels/" + vesselA + "/calendar")
                        .param("from", data)
                        .param("to", data))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[0].alto_mar.disponivel").value(false));
    }

    private String registerVessel(String ownerId, String nomeLegal) throws Exception {
        var request = new RegisterVesselRequest(
                ownerId, nomeLegal, "Fantasia", "CP-" + nomeLegal.hashCode(),
                "00.000.000/0001-00", 20, "Porto Teste");

        String responseBody = mockMvc.perform(post("/vessels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(responseBody).get("id").asText();
    }

    private void seedConfirmedBookingCount(String vesselId, String data, String tipoPasseio, int count) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("VESSEL#" + vesselId));
        item.put("SK", s("BOOKINGCOUNT#" + data + "#" + tipoPasseio));
        item.put("count", n(count));
        putItem(item);
    }

    private record RegisterVesselRequest(
            String ownerId,
            String nomeLegal,
            String nomeFantasia,
            String numeroRegistroCapitania,
            String cpfCnpjProprietario,
            int capacidadeMaxima,
            String portoSaida) {
    }

    private record SetAvailabilityRequest(boolean disponivel, String motivo) {
    }

    private record SetSeatLimitRequest(Integer limite) {
    }
}
