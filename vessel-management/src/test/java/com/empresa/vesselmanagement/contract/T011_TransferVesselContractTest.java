package com.empresa.vesselmanagement.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.support.AbstractDynamoDbIntegrationTest;

/**
 * Contract test: POST /vessels/{id}/transfer e DELETE /vessels/{id} (FR-002).
 *
 * FR-002: remoção de embarcação com reservas futuras confirmadas não é permitida
 * diretamente — exige transferir antes para outra embarcação compatível (mesma
 * capacidade mínima e porto de saída). Sem reservas futuras, a remoção é direta.
 *
 * O cenário completo (com reservas futuras de fato bloqueando a remoção) é do
 * teste de integração T020; aqui cobrimos o formato dos dois endpoints e o
 * caminho "sem reservas futuras" (remoção direta).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T011_TransferVesselContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedVesselFixtures() {
        seedVessel(vessel("vessel-1", "Porto A", 20));
        // compatível: mesmo porto, capacidade >= origem (FR-002: "mesma capacidade mínima e porto de saída")
        seedVessel(vessel("vessel-2", "Porto A", 20));
        // incompatível: porto de saída diferente
        seedVessel(vessel("vessel-incompativel", "Porto B", 20));
        seedVessel(vessel("vessel-sem-reservas", "Porto A", 20));
    }

    private Vessel vessel(String id, String portoSaida, int capacidadeMaxima) {
        return Vessel.builder()
                .id(id)
                .ownerId("owner-t011")
                .nomeLegal("Nome Legal " + id)
                .nomeFantasia("Fantasia " + id)
                .numeroRegistroCapitania("CP-" + id)
                .cpfCnpjProprietario("000.000.000-00")
                .capacidadeMaxima(capacidadeMaxima)
                .portoSaida(portoSaida)
                .status(VesselStatus.PENDENTE_CONFIGURACAO)
                .build();
    }

    @Test
    void deveTransferirReservasParaEmbarcacaoCompativel() throws Exception {
        var request = new TransferRequest("vessel-2");

        mockMvc.perform(post("/vessels/vessel-1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetVesselId").value("vessel-2"))
                .andExpect(jsonPath("$.transferredBookingsCount").isNumber());
    }

    @Test
    void deveRecusarTransferenciaParaEmbarcacaoIncompativel() throws Exception {
        var request = new TransferRequest("vessel-incompativel");

        mockMvc.perform(post("/vessels/vessel-1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INCOMPATIBLE_TARGET_VESSEL"));
    }

    @Test
    void deveRemoverDiretamenteEmbarcacaoSemReservasFuturas() throws Exception {
        mockMvc.perform(delete("/vessels/vessel-sem-reservas"))
                .andExpect(status().isNoContent());
    }

    private record TransferRequest(String targetVesselId) {
    }
}
