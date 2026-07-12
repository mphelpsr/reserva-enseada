package com.empresa.vesselmanagement.domain.seatlimit;

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
 * VESSEL#{vesselId} / COUNTER#DEFAULTSEAT (plan.md — Phase 1, Data Model). FR-015.
 * Contador cumulativo por embarcação (não reinicia por dia) de quantas vezes o
 * padrão automático de 10% já foi aplicado. Limite: 2 aplicações.
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DefaultSeatUsageCounter {

    public static final String SK = "COUNTER#DEFAULTSEAT";
    public static final int LIMITE_APLICACOES = 2;

    private String vesselId;
    private int vezesAplicado;

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
        return SK;
    }

    public void setSk(String sk) {
        // fixo
    }

    public boolean podeAplicarPadrao() {
        return vezesAplicado < LIMITE_APLICACOES;
    }
}
