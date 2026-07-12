package com.empresa.vesselmanagement.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Integration test: conflito rodízio x Alto Mar — o rodízio sempre prevalece,
 * a ação exige escolha explícita do proprietário (FR-014, cenário 3b do spec.md).
 *
 * Given um dia já cadastrado como rodízio para uma embarcação,
 * When o proprietário tenta marcar Alto Mar como disponível nesse mesmo dia,
 * Then o sistema interrompe com 409 estruturado e NÃO aplica a mudança — o
 * calendário continua mostrando Alto Mar indisponível até decisão explícita.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T017_RotationAvailabilityConflictIntegrationTest extends AbstractDynamoDbIntegrationTest {

    private static final String VESSEL_ID = "vessel-rotation-conflict";
    private static final String DATE = "2026-10-05";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rodizioDevePrevalecerSobreTentativaDeMarcarAltoMarDisponivel() throws Exception {
        // 1) proprietário cadastra rodízio para o dia
        mockMvc.perform(put("/vessels/" + VESSEL_ID + "/rotation/" + DATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetRotationRequest(true))))
                .andExpect(status().isOk());

        // 2) tenta marcar Alto Mar disponível no mesmo dia -> interrompido, 409 estruturado
        mockMvc.perform(put("/vessels/" + VESSEL_ID + "/availability/" + DATE + "/alto_mar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetAvailabilityRequest(true, null))))
                .andExpect(status().isConflict())
                // mesmo código de erro do teste de contrato T013 (PUT rotation) — é o mesmo
                // conflito de FR-014, só disparado a partir do lado da disponibilidade
                .andExpect(jsonPath("$.error").value("ROTATION_AVAILABILITY_CONFLICT"))
                .andExpect(jsonPath("$.options").isArray());

        // 3) a coexistência silenciosa não é permitida: o calendário continua mostrando
        // Alto Mar indisponível (rodízio prevalece até decisão explícita do proprietário)
        mockMvc.perform(get("/vessels/" + VESSEL_ID + "/calendar")
                        .param("from", DATE)
                        .param("to", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[0].alto_mar.disponivel").value(false));
    }

    @Test
    void orlaNaoEAfetadaPeloRodizioDeAltoMar() throws Exception {
        mockMvc.perform(put("/vessels/" + VESSEL_ID + "/rotation/" + DATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SetRotationRequest(true))));

        // FR-013: rodízio bloqueia SÓ alto_mar; orla segue sob controle normal do proprietário
        mockMvc.perform(put("/vessels/" + VESSEL_ID + "/availability/" + DATE + "/orla")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetAvailabilityRequest(true, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(true));
    }

    private record SetRotationRequest(boolean bloqueado) {
    }

    private record SetAvailabilityRequest(boolean disponivel, String motivo) {
    }
}
