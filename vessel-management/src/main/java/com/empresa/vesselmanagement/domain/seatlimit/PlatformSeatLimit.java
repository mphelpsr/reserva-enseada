package com.empresa.vesselmanagement.domain.seatlimit;

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
 * VESSEL#{vesselId} / SEATLIMIT#{data}#{tipoPasseio} (plan.md — Phase 1, Data Model).
 * FR-015: vagas disponibilizadas para venda na plataforma, sempre ≤ capacidade máxima.
 * Reduzir o limite NUNCA é bloqueado e NUNCA invalida reservas existentes (Opção C,
 * Princípio II) — vagas restantes são sempre calculadas como max(0, limite−vendidas−retidas)
 * pelo módulo booking, que é quem tem essa informação.
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSeatLimit {

    private String vesselId;
    private LocalDate data;
    private TourType tipoPasseio;
    private int limite;
    private SeatLimitOrigin origem;

    public static String skFor(LocalDate data, TourType tipoPasseio) {
        return "SEATLIMIT#" + data + "#" + tipoPasseio.getValue();
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
}
