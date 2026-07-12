package com.empresa.vesselmanagement.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Contract test: POST /vessels (FR-001, FR-009).
 *
 * FR-001: nome, capacidade máxima e porto/local de saída são obrigatórios.
 * FR-009: identificador único = nº registro Capitania + CPF/CNPJ do proprietário + nome legal;
 * nome fantasia é campo separado (exibido ao comprador).
 *
 * Fase 3.2 (gate de TDD): nenhum controller existe ainda, então todo request aqui
 * retorna 404 — os asserts abaixo descrevem o contrato final e devem falhar hoje.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T009_RegisterVesselContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveCadastrarEmbarcacaoComStatusPendenteConfiguracao() throws Exception {
        var request = new RegisterVesselRequest(
                "owner-1",
                "Sereia do Mar",
                "Passeios Sereia",
                "CP-12345",
                "12.345.678/0001-90",
                20,
                "Porto de Búzios");

        mockMvc.perform(post("/vessels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").value("owner-1"))
                .andExpect(jsonPath("$.nomeLegal").value("Sereia do Mar"))
                .andExpect(jsonPath("$.nomeFantasia").value("Passeios Sereia"))
                .andExpect(jsonPath("$.capacidadeMaxima").value(20))
                .andExpect(jsonPath("$.status").value("pendente_configuracao"));
    }

    @Test
    void deveRecusarCadastroSemCamposObrigatorios() throws Exception {
        var request = new RegisterVesselRequest(
                "owner-1", null, "Passeios Sereia", "CP-12345", "12.345.678/0001-90", 20, "Porto de Búzios");

        mockMvc.perform(post("/vessels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRecusarIdentificadorUnicoDuplicado() throws Exception {
        var request = new RegisterVesselRequest(
                "owner-2", "Sereia do Mar", "Outro Nome", "CP-12345", "12.345.678/0001-90", 15, "Porto de Búzios");

        // primeiro cadastro (idêntico ao do teste acima: mesmo registro + CPF/CNPJ + nome legal)
        mockMvc.perform(post("/vessels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // segunda tentativa com o mesmo identificador único (FR-009) deve ser recusada
        mockMvc.perform(post("/vessels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
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
}
