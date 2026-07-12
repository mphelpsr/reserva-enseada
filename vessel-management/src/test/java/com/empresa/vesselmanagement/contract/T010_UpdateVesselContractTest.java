package com.empresa.vesselmanagement.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.empresa.vesselmanagement.support.AbstractDynamoDbIntegrationTest;

/**
 * Contract test: PATCH /vessels/{id} (FR-002).
 *
 * Edição de dados cadastrais. A ativação (status=ativa) sem payment_recebedor_id
 * válido do proprietário (FR-016) é coberta em detalhe pelo teste de integração T019 —
 * aqui só o formato básico do contrato.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T010_UpdateVesselContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveAtualizarDadosCadastraisDaEmbarcacao() throws Exception {
        var request = new UpdateVesselRequest("Novo Nome Fantasia", "Novo Porto", null);

        mockMvc.perform(patch("/vessels/vessel-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("vessel-1"))
                .andExpect(jsonPath("$.nomeFantasia").value("Novo Nome Fantasia"))
                .andExpect(jsonPath("$.portoSaida").value("Novo Porto"));
    }

    @Test
    void deveRetornar404ParaEmbarcacaoInexistente() throws Exception {
        var request = new UpdateVesselRequest("Novo Nome Fantasia", null, null);

        mockMvc.perform(patch("/vessels/inexistente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    private record UpdateVesselRequest(String nomeFantasia, String portoSaida, String status) {
    }
}
