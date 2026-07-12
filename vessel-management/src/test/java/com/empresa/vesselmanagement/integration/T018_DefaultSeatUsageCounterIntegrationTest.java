package com.empresa.vesselmanagement.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.empresa.vesselmanagement.support.AbstractDynamoDbIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration test: contador de padrão automático de vagas — 1ª e 2ª ausência de
 * indicação aplicam 10% da capacidade máxima; da 3ª vez em diante, zero vagas
 * (FR-015, cenários 6/6a do spec.md). Contador é cumulativo por embarcação, não
 * reinicia por dia.
 *
 * Embarcação com capacidadeMaxima=20 -> 10% = 2 (arredondado para baixo).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T018_DefaultSeatUsageCounterIntegrationTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void primeirasDuasAusenciasAplicamPadraoDezPorCentoTerceiraEmDianteZeraVagas() throws Exception {
        String vesselId = registerVesselWithCapacity(20);

        // 1ª ausência de indicação: aplica 10% (2 vagas), contador vai a 1
        mockMvc.perform(put("/vessels/" + vesselId + "/seat-limit/2026-11-01/alto_mar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(2))
                .andExpect(jsonPath("$.origem").value("PADRAO_AUTOMATICO"))
                .andExpect(jsonPath("$.vezesPadraoAplicado").value(1));

        // 2ª ausência de indicação (dia diferente): aplica 10% de novo, contador vai a 2
        mockMvc.perform(put("/vessels/" + vesselId + "/seat-limit/2026-11-02/alto_mar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(2))
                .andExpect(jsonPath("$.origem").value("PADRAO_AUTOMATICO"))
                .andExpect(jsonPath("$.vezesPadraoAplicado").value(2));

        // 3ª ausência de indicação: contador já em 2, NÃO aplica padrão -> zero vagas
        mockMvc.perform(put("/vessels/" + vesselId + "/seat-limit/2026-11-03/alto_mar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(0))
                .andExpect(jsonPath("$.origem").value("ZERO_SEM_PADRAO"))
                .andExpect(jsonPath("$.vezesPadraoAplicado").value(2));
    }

    @Test
    void indicacaoExplicitaNaoConsomeContadorDoPadrao() throws Exception {
        String vesselId = registerVesselWithCapacity(20);

        mockMvc.perform(put("/vessels/" + vesselId + "/seat-limit/2026-11-01/alto_mar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetSeatLimitRequest(7))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origem").value("MANUAL"));

        // contador segue zerado -> próxima ausência de indicação ainda aplica o padrão
        mockMvc.perform(put("/vessels/" + vesselId + "/seat-limit/2026-11-02/alto_mar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origem").value("PADRAO_AUTOMATICO"))
                .andExpect(jsonPath("$.vezesPadraoAplicado").value(1));
    }

    private String registerVesselWithCapacity(int capacidadeMaxima) throws Exception {
        var request = new RegisterVesselRequest(
                "owner-seatcount", "Embarcação " + capacidadeMaxima, "Fantasia", "CP-" + capacidadeMaxima,
                "00.000.000/0001-00", capacidadeMaxima, "Porto Teste");

        String responseBody = mockMvc.perform(post("/vessels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(responseBody).get("id").asText();
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

    private record SetSeatLimitRequest(Integer limite) {
    }
}
