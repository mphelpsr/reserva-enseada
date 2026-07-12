package com.empresa.booking.domain.seathold;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.infrastructure.dynamodb.converter.TourTypeConverter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * HOLD#{id} / METADATA (plan.md — Phase 1, Data Model). FR-003, FR-004:
 * retenção temporária de vagas durante o checkout, no máximo 10 minutos.
 *
 * `ttl` é o atributo nativo do DynamoDB (infra/dynamodb.tf) — reforço de
 * limpeza física em até 48h, NUNCA a fonte de verdade: toda leitura de vagas
 * restantes ignora holds com `expiresAt` vencido na aplicação, independente
 * do item ainda existir fisicamente (CLAUDE.md, mesma ressalva de plan.md).
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatHold {

    public static final String SK = "METADATA";

    private String id;
    private String buyerId;
    private String vesselId;
    private LocalDate data;

    @Getter(onMethod_ = @DynamoDbConvertedBy(TourTypeConverter.class))
    private TourType tipoPasseio;

    private int quantidade;
    private Instant expiresAt;
    private Long valorTotalCentavos;

    public static String pkFor(String holdId) {
        return "HOLD#" + holdId;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
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

    /** Espelha `expiresAt` em epoch seconds — atributo TTL nativo do DynamoDB (infra/dynamodb.tf). */
    @DynamoDbAttribute("ttl")
    @JsonIgnore
    public Long getTtl() {
        return expiresAt == null ? null : expiresAt.getEpochSecond();
    }

    public void setTtl(Long ttl) {
        // derivado de `expiresAt`, nunca lido de volta (a aplicação sempre confia em `expiresAt`)
    }
}
