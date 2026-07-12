package com.empresa.vesselmanagement.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.support.AbstractDynamoDbIntegrationTest;

/**
 * Contract test: PUT /vessels/{id}/rotation/{data} (FR-013, FR-014), incluindo a
 * resposta 409 estruturada quando Alto Mar já está disponível nesse dia/embarcação.
 *
 * FR-014: o rodízio SEMPRE prevalece; o sistema não permite a coexistência
 * silenciosa dos dois estados — interrompe com uma pergunta explícita, oferecendo
 * (a) mudar o dia da disponibilidade de Alto Mar ou (b) alterar/remover o rodízio.
 * O cenário completo de ponta a ponta (rodízio cadastrado primeiro, depois tentativa
 * de marcar Alto Mar) é do teste de integração T017; aqui, o caminho inverso —
 * Alto Mar já disponível, tentativa de cadastrar rodízio no mesmo dia.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T013_SetRotationContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedVesselFixture() {
        seedVessel(Vessel.builder()
                .id("vessel-1")
                .ownerId("owner-t013")
                .nomeLegal("Nome Legal")
                .nomeFantasia("Fantasia")
                .numeroRegistroCapitania("CP-T013")
                .cpfCnpjProprietario("000.000.000-00")
                .capacidadeMaxima(20)
                .portoSaida("Porto Teste")
                .status(VesselStatus.PENDENTE_CONFIGURACAO)
                .build());
    }

    @Test
    void deveCadastrarRodizioParaDiaSemConflito() throws Exception {
        var request = new SetRotationRequest(true);

        mockMvc.perform(put("/vessels/vessel-1/rotation/2026-09-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vesselId").value("vessel-1"))
                .andExpect(jsonPath("$.data").value("2026-09-01"))
                .andExpect(jsonPath("$.bloqueado").value(true));
    }

    @Test
    void deveRetornar409EstruturadoQuandoAltoMarJaDisponivelNoDia() throws Exception {
        // pré-condição: Alto Mar já marcado disponível nesse dia (fora do escopo deste
        // teste de contrato — asserção de fluxo completo fica em T017); aqui validamos
        // apenas o FORMATO da resposta de conflito, usando uma data convencionada nos
        // fixtures de teste como "já tem Alto Mar disponível".
        seedAvailability(DeclaredAvailability.builder()
                .vesselId("vessel-1")
                .data(LocalDate.parse("2026-09-02"))
                .tipoPasseio(TourType.ALTO_MAR)
                .disponivel(true)
                .build());

        var request = new SetRotationRequest(true);

        mockMvc.perform(put("/vessels/vessel-1/rotation/2026-09-02")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ROTATION_AVAILABILITY_CONFLICT"))
                .andExpect(jsonPath("$.options").isArray())
                .andExpect(jsonPath("$.options", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.options[0].action").value("ALTERAR_DISPONIBILIDADE_ALTO_MAR"))
                .andExpect(jsonPath("$.options[1].action").value("ALTERAR_OU_REMOVER_RODIZIO"));
    }

    private record SetRotationRequest(boolean bloqueado) {
    }
}
