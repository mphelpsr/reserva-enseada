package com.empresa.vesselmanagement.domain.bookingcount;

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
 * VESSEL#{vesselId} / BOOKINGCOUNT#{data}#{tipoPasseio} — decisão de 2026-07-12 em
 * plan-vessel-management.md ("Eventos Consumidos"). Réplica local somente-leitura do
 * nº de reservas confirmadas, mantida por T059b via o evento `booking.confirmed`
 * (e decrementada por T059 via `booking.cancelled`). NÃO é fonte de verdade de
 * reservas — só existe para SetAvailabilityUseCase (T041) e RemoveVesselUseCase
 * (T040b) decidirem entre FR-004 (efeito imediato) e FR-007 (Saga de
 * transferência/cancelamento).
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmedBookingCount {

    private String vesselId;
    private LocalDate data;
    private TourType tipoPasseio;
    private int count;

    public static String skFor(LocalDate data, TourType tipoPasseio) {
        return "BOOKINGCOUNT#" + data + "#" + tipoPasseio.getValue();
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
        return skFor(data, tipoPasseio);
    }

    public void setSk(String sk) {
        // derivado de `data`/`tipoPasseio`
    }

    public boolean temReservaConfirmada() {
        return count > 0;
    }
}
