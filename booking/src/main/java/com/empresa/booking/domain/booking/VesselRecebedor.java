package com.empresa.booking.domain.booking;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * VESSEL#{vesselId} / RECEBEDOR (plan.md — "Contrato da Saga"). Réplica local
 * somente-leitura da chave Pix (`payment_recebedor_id`) do proprietário
 * daquela embarcação, mantida via evento `vessel.recebedor.changed` (T052b,
 * publicado do lado vessel-management por T059c). `ConfirmBookingUseCase`
 * (T039) consulta este item pra montar o repasse via split Pix (FR-015) —
 * sem réplica, a confirmação é recusada (nunca chama o provedor sem uma
 * chave Pix válida).
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VesselRecebedor {

    public static final String SK = "RECEBEDOR";

    private String vesselId;
    private String pixKey;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    @JsonIgnore
    public String getPk() {
        return "VESSEL#" + vesselId;
    }

    public void setPk(String pk) {
        // derivado de `vesselId`
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    @JsonIgnore
    public String getSk() {
        return SK;
    }

    public void setSk(String sk) {
        // fixo
    }
}
