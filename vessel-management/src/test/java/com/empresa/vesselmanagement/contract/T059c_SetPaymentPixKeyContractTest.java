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

import com.empresa.vesselmanagement.support.AbstractDynamoDbIntegrationTest;

/** Contract test: PUT /owners/{ownerId}/payment-pix-key (T059c, FR-016). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T059c_SetPaymentPixKeyContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deveGravarAChavePixDoProprietario() throws Exception {
        var request = new SetPaymentPixKeyRequest("chave-pix-t059c");

        mockMvc.perform(put("/owners/owner-t059c/payment-pix-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value("owner-t059c"))
                .andExpect(jsonPath("$.pixKey").value("chave-pix-t059c"));
    }

    @Test
    void deveRecusarChavePixVazia() throws Exception {
        var request = new SetPaymentPixKeyRequest("");

        mockMvc.perform(put("/owners/owner-t059c/payment-pix-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private record SetPaymentPixKeyRequest(String pixKey) {
    }
}
