package com.empresa.vesselmanagement.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Contract test: PUT /vessels/{id}/availability/{data}/{tipoPasseio} (FR-003, FR-004).
 *
 * A marcação do proprietário É a disponibilidade final — sem aprovação externa.
 * O conflito com rodízio (FR-014) é do teste de integração T017; o caminho "dia com
 * reserva confirmada" (FR-007) é do teste de integração T021. Aqui só o caminho
 * simples: marcar/desmarcar um dia sem nenhuma reserva envolvida.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T012_SetAvailabilityContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveMarcarDiaComoDisponivel() throws Exception {
        var request = new SetAvailabilityRequest(true, null);

        mockMvc.perform(put("/vessels/vessel-1/availability/2026-08-10/orla")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vesselId").value("vessel-1"))
                .andExpect(jsonPath("$.data").value("2026-08-10"))
                .andExpect(jsonPath("$.tipoPasseio").value("orla"))
                .andExpect(jsonPath("$.disponivel").value(true));
    }

    @Test
    void deveDesmarcarDiaSemReservaComEfeitoImediato() throws Exception {
        var request = new SetAvailabilityRequest(false, "manutenção programada");

        mockMvc.perform(put("/vessels/vessel-1/availability/2026-08-11/orla")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponivel").value(false))
                .andExpect(jsonPath("$.motivo").value("manutenção programada"));
    }

    @Test
    void deveRecusarTipoPasseioInvalido() throws Exception {
        var request = new SetAvailabilityRequest(true, null);

        mockMvc.perform(put("/vessels/vessel-1/availability/2026-08-10/passeio-invalido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private record SetAvailabilityRequest(boolean disponivel, String motivo) {
    }
}
