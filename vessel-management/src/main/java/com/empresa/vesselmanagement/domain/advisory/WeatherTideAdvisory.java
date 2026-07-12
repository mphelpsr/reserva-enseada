package com.empresa.vesselmanagement.domain.advisory;

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
 * VESSEL#{vesselId} / ADVISORY#{data} (plan.md — Phase 1, Data Model). FR-006, FR-008.
 * Escrito só pelo job assíncrono (AdvisoryCalculationJob, T057) — GetAdvisoryUseCase
 * (T045) é estritamente leitura, nunca escreve em DeclaredAvailability (Princípio I).
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherTideAdvisory {

    private String vesselId;
    private LocalDate data;
    private AdvisoryCondition condicao;
    private String detalhes;

    public static String skFor(LocalDate data) {
        return "ADVISORY#" + data;
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
