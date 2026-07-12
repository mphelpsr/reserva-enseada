package com.empresa.vesselmanagement.domain.availability;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.empresa.vesselmanagement.domain.vessel.Vessel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * VESSEL#{vesselId} / AVAIL#{data}#{tipoPasseio} (plan.md — Phase 1, Data Model).
 * FR-003, FR-004: a marcação do proprietário É a disponibilidade final — sem
 * validação externa (Princípio I).
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeclaredAvailability {

    private String vesselId;
    private LocalDate data;
    private TourType tipoPasseio;
    private boolean disponivel;
    private String motivo;

    public static String skFor(LocalDate data, TourType tipoPasseio) {
        return "AVAIL#" + data + "#" + tipoPasseio.getValue();
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
