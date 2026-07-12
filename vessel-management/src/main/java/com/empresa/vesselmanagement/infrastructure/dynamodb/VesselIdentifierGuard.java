package com.empresa.vesselmanagement.infrastructure.dynamodb;

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
 * Item-guarda para o identificador único de FR-009 (nº registro Capitania +
 * CPF/CNPJ do proprietário + nome legal). Não é uma entidade de domínio — existe só
 * para o DynamoDB rejeitar, via `attribute_not_exists(PK)` num `TransactWriteItems`,
 * um segundo cadastro com o mesmo identificador (ver VesselRepository.save).
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VesselIdentifierGuard {

    public static final String SK = "METADATA";

    private String uniqueKey;
    private String vesselId;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    @JsonIgnore
    public String getPk() {
        return uniqueKey;
    }

    public void setPk(String pk) {
        // derivado de `uniqueKey`
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
