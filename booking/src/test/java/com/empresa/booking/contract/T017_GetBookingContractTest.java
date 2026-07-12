package com.empresa.booking.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.empresa.booking.support.AbstractDynamoDbIntegrationTest;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Contract test: GET /bookings/{id} — detalhe de uma reserva. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class T017_GetBookingContractTest extends AbstractDynamoDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveRetornarDetalheDaReserva() throws Exception {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s("BOOKING#booking-detalhe"));
        item.put("SK", s("METADATA"));
        item.put("id", s("booking-detalhe"));
        item.put("buyerId", s("buyer-1"));
        item.put("vesselId", s("vessel-1"));
        item.put("status", s("confirmada"));
        putItem(item);

        mockMvc.perform(get("/bookings/booking-detalhe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("booking-detalhe"))
                .andExpect(jsonPath("$.status").value("confirmada"));
    }

    @Test
    void deveRetornar404ParaReservaInexistente() throws Exception {
        mockMvc.perform(get("/bookings/inexistente"))
                .andExpect(status().isNotFound());
    }
}
