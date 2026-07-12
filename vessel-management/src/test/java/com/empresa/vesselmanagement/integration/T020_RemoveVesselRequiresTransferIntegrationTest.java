package com.empresa.vesselmanagement.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Integration test: remoção de embarcação com reservas futuras exige transferência
 * prévia (FR-002).
 *
 * A réplica local `ConfirmedBookingCount` (BOOKINGCOUNT#data#tipoPasseio — decisão
 * de 2026-07-12 em plan-vessel-management.md, mantida por T059b via evento
 * `booking.confirmed`) é o dado que `RemoveVesselUseCase` (T040b) consulta para
 * decidir entre remoção direta e a exigência de transferência. Este teste semeia
 * esse contador diretamente (T059b ainda não existe) para exercitar o portão de
 * remoção via a API real (DELETE /vessels/{id}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T020_RemoveVesselRequiresTransferIntegrationTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveRemoverDiretamenteSemReservasFuturasConfirmadas() throws Exception {
        String vesselId = registerVessel("owner-sem-reservas", "Sem Reservas");

        mockMvc.perform(delete("/vessels/" + vesselId))
                .andExpect(status().isNoContent());
    }

    @Test
    void deveExigirTransferenciaPreviaQuandoHaReservaFuturaConfirmada() throws Exception {
        String ownerId = "owner-com-reservas";
        String vesselId = registerVessel(ownerId, "Com Reservas Futuras");
        String targetVesselId = registerVessel(ownerId, "Frota Compatível");

        // reserva confirmada futura, replicada via booking.confirmed (T059b, fora de escopo aqui)
        seedConfirmedBookingCount(vesselId, "2026-12-20", "alto_mar", 1);

        // remoção direta é recusada — precisa transferir antes (FR-002)
        mockMvc.perform(delete("/vessels/" + vesselId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VESSEL_HAS_FUTURE_BOOKINGS"))
                .andExpect(jsonPath("$.requiresTransferFirst").value(true));

        // transferência para embarcação compatível da mesma frota é aceita
        mockMvc.perform(post("/vessels/" + vesselId + "/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferRequest(targetVesselId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetVesselId").value(targetVesselId));
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

    private record TransferRequest(String targetVesselId) {
    }
}
