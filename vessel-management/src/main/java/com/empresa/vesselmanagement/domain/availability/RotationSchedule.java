package com.empresa.vesselmanagement.domain.availability;

import java.time.LocalDate;

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
 * VESSEL#{vesselId} / ROTATION#{data} (plan.md — Phase 1, Data Model). FR-013, FR-014.
 *
 * Um dia de rodízio bloqueia SÓ `alto_mar` (Orla nunca é afetada — Princípio I).
 * É a única indisponibilidade que o sistema impõe automaticamente.
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RotationSchedule {

    private String vesselId;
    private LocalDate data;
    private boolean bloqueado;

    public static String skFor(LocalDate data) {
        return "ROTATION#" + data;
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
        return skFor(data);
    }

    public void setSk(String sk) {
        // derivado de `data`
    }
}
