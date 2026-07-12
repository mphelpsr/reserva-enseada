package com.empresa.vesselmanagement.domain.cancellation;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.empresa.vesselmanagement.domain.vessel.Vessel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * VESSEL#{vesselId} / TRANSFER#{id} (plan.md — Phase 1, Data Model). FR-002.
 *
 * Registro de transferência de reservas futuras da embarcação de origem (que está
 * sendo removida) para uma embarcação de destino compatível (mesma capacidade
 * mínima e porto de saída) — passo obrigatório antes de RemoveVesselUseCase (T040b)
 * concluir a remoção quando há reservas futuras confirmadas.
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VesselTransfer {

    private String id;
    private String vesselId;
    private String targetVesselId;
    private int transferredBookingsCount;
    private Instant transferredAt;

    public static String skFor(String id) {
        return "TRANSFER#" + id;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    @JsonIgnore
    public String getPk() {
        return Vessel.pkFor(vesselId);
    }

    public void setPk(String pk) {
        // derivado de `vesselId`
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    @JsonIgnore
    public String getSk() {
        return skFor(id);
    }

    public void setSk(String sk) {
        // derivado de `id`
    }
}
