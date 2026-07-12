package com.empresa.booking.domain.booking;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/** BUYER#{id} / METADATA — identidade do comprador (resolvida via Cognito, T003/T058). */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Buyer {

    public static final String SK = "METADATA";

    private String id;
    private String nome;
    private String email;
    private String telefone;

    public static String pkFor(String buyerId) {
        return "BUYER#" + buyerId;
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
}
