package com.empresa.vesselmanagement.domain.cancellation;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.empresa.vesselmanagement.domain.availability.TourType;
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
 * VESSEL#{vesselId} / TRANSFER#{id} (plan.md — Phase 1, Data Model). FR-007, Princípio VII.
 *
 * Registro da Saga leve de CancelDayWithBookingsUseCase (T046): busca embarcação da
 * mesma frota com vaga para o dia/tipo de passeio afetado; se achar, fica
 * VIABLE_PENDING até o booking confirmar (`booking.transferred`) ou expirar/recusar
 * (`booking.cancelled`) — fechado por T059. Se não achar, nasce já
 * CANCELLED_NO_ALTERNATIVE (reembolso integral imediato, sem janela de análise).
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingTransferAttempt {

    private String id;
    private String vesselId;
    private LocalDate data;
    private TourType tipoPasseio;
    private String motivo;
    private String targetVesselId;
    private TransferAttemptStatus status;

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
