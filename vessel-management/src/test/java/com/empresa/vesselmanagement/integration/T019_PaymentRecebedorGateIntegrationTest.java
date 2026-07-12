package com.empresa.vesselmanagement.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Integration test: embarcação não pode ficar `ativa` sem `payment_recebedor_id`
 * válido do proprietário (FR-016, cenário 7 do spec.md).
 *
 * O cadastro do payment_recebedor_id em si (onboarding Pagar.me) é integração
 * externa fora do escopo deste módulo/plan — por isso este teste semeia o estado
 * do proprietário DIRETO na tabela (contornando a app, que ainda não existe),
 * e exercita só o portão de ativação via a API real (PATCH /vessels/{id}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T019_PaymentRecebedorGateIntegrationTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void naoDevePermitirAtivacaoSemPaymentRecebedorIdValido() throws Exception {
        String ownerId = "owner-sem-recebedor";
        seedOwner(ownerId, null);
        String vesselId = registerVessel(ownerId);

        mockMvc.perform(patch("/vessels/" + vesselId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateVesselRequest(null, null, "ativa"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PAYMENT_RECEBEDOR_ID_MISSING"));
    }

    @Test
    void devePermitirAtivacaoComPaymentRecebedorIdValido() throws Exception {
        String ownerId = "owner-com-recebedor";
        seedOwner(ownerId, "recebedor-pagarme-123");
        String vesselId = registerVessel(ownerId);

        mockMvc.perform(patch("/vessels/" + vesselId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateVesselRequest(null, null, "ativa"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ativa"));
    }

    private void seedOwner(String ownerId, String paymentRecebedorId) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("OWNER#" + ownerId));
        item.put("SK", s("METADATA"));
        if (paymentRecebedorId != null) {
            item.put("paymentRecebedorId", s(paymentRecebedorId));
        }
        putItem(item);
    }

    private String registerVessel(String ownerId) throws Exception {
        var request = new RegisterVesselRequest(
                ownerId, "Embarcação " + ownerId, "Fantasia", "CP-" + ownerId,
                "00.000.000/0001-00", 20, "Porto Teste");

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

    private record UpdateVesselRequest(String nomeFantasia, String portoSaida, String status) {
    }
}
