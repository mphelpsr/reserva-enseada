package com.empresa.vesselmanagement.domain.vessel;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * OWNER#{id} / METADATA (plan.md — Phase 1, Data Model). `paymentRecebedorId` é o
 * portão de ativação de qualquer embarcação do proprietário (FR-016).
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Owner {

    public static final String SK = "METADATA";

    private String id;
    private String paymentRecebedorId;

    public static String pkFor(String ownerId) {
        return "OWNER#" + ownerId;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    @JsonIgnore
    public String getPk() {
        return pkFor(id);
    }

    public void setPk(String pk) {
        // derivado de `id`
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

    public boolean hasValidPaymentRecebedorId() {
        return paymentRecebedorId != null && !paymentRecebedorId.isBlank();
    }
}
