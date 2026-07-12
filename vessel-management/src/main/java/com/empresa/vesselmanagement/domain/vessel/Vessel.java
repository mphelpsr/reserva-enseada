package com.empresa.vesselmanagement.domain.vessel;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * VESSEL#{id} / METADATA (plan.md — Phase 1, Data Model). FR-001, FR-009, FR-011.
 *
 * GSI1 (gsi1Pk=OWNER#{ownerId}, gsi1Sk=VESSEL#{id}) vive no próprio item de metadata
 * — não há item de índice separado (decisão de implementação: attributes esparsos na
 * mesma linha em vez de item duplicado, evita escrita dupla para manter consistência).
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vessel {

    public static final String SK = "METADATA";

    private String id;
    private String ownerId;
    private String nomeLegal;
    private String nomeFantasia;
    private String numeroRegistroCapitania;
    private String cpfCnpjProprietario;
    private Integer capacidadeMaxima;
    private String portoSaida;
    private VesselStatus status;

    /**
     * Coordenadas do porto de saída, opcionais — FR-001 não exige geolocalização,
     * só o texto livre `portoSaida`. Sem lat/lon, AdvisoryCalculationJob (T057) pula
     * a embarcação (não há como consultar o Stormglass sem coordenadas). Preenchidas
     * manualmente por enquanto; um passo de geocoding fica para uma iteração futura.
     */
    private Double latitude;
    private Double longitude;

    public static String pkFor(String vesselId) {
        return "VESSEL#" + vesselId;
    }

    /** Identificador único do FR-009: nº registro Capitania + CPF/CNPJ do proprietário + nome legal. */
    public String uniqueIdentifierKey() {
        return "VESSELIDENTIFIER#" + numeroRegistroCapitania + "#" + cpfCnpjProprietario + "#" + nomeLegal;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    @JsonIgnore
    public String getPk() {
        return pkFor(id);
    }

    public void setPk(String pk) {
        // derivado de `id` — persistido só para satisfazer o schema da tabela, nunca lido de volta
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

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI1")
    @DynamoDbAttribute("GSI1PK")
    @JsonIgnore
    public String getGsi1Pk() {
        return "OWNER#" + ownerId;
    }

    public void setGsi1Pk(String gsi1Pk) {
        // derivado de `ownerId`
    }

    @DynamoDbSecondarySortKey(indexNames = "GSI1")
    @DynamoDbAttribute("GSI1SK")
    @JsonIgnore
    public String getGsi1Sk() {
        return pkFor(id);
    }

    public void setGsi1Sk(String gsi1Sk) {
        // derivado de `id`
    }
}
