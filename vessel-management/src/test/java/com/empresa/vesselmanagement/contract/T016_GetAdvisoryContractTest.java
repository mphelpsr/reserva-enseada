package com.empresa.vesselmanagement.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract test: GET /vessels/{id}/advisory/{data} (FR-006, FR-008).
 *
 * O indicador é só um ALERTA (Princípio I) — este teste garante que o endpoint
 * expõe o dado, nunca que ele altera DeclaredAvailability (isso é garantido por
 * GetAdvisoryUseCase ser somente-leitura, verificado na Fase 3.3).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T016_GetAdvisoryContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveRetornarIndicadorDeMareEPrevisaoDoDia() throws Exception {
        mockMvc.perform(get("/vessels/vessel-1/advisory/2026-08-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vesselId").value("vessel-1"))
                .andExpect(jsonPath("$.data").value("2026-08-10"))
                .andExpect(jsonPath("$.condicao").isString());
    }

    @Test
    void deveRetornar404QuandoAdvisoryAindaNaoCalculado() throws Exception {
        mockMvc.perform(get("/vessels/vessel-1/advisory/2099-01-01"))
                .andExpect(status().isNotFound());
    }
}
