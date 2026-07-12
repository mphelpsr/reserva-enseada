package com.empresa.booking.domain.booking;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.infrastructure.dynamodb.converter.BookingStatusConverter;
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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * BOOKING#{id} / METADATA (plan.md — Phase 1, Data Model). FR-005, FR-015.
 *
 * GSI1 (gsi1Pk=BUYER#{buyerId}, gsi1Sk=BOOKING#{id}) vive no próprio item —
 * mesma decisão de vessel-management (Vessel), sem item de índice separado.
 * O índice por embarcação/dia (VESSEL#{vesselId} / BOOKING#{data}#{id},
 * FR-008/FR-009) é um item PONTEIRO separado, escrito por BookingRepository
 * junto com este na mesma transação — não cabe no mesmo bean porque o
 * Enhanced Client deriva só um PK/SK por classe.
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    public static final String SK = "METADATA";

    private String id;
    private String buyerId;
    private String vesselId;
    private LocalDate data;

    @Getter(onMethod_ = @DynamoDbConvertedBy(TourTypeConverter.class))
    private TourType tipoPasseio;

    private int quantidade;

    @Getter(onMethod_ = @DynamoDbConvertedBy(BookingStatusConverter.class))
    private BookingStatus status;

    private Long valorPagoCentavos;
    private Long valorComissaoCentavos;
    private Long valorLiquidoCentavos;

    private Instant compradaEm;

    /** Motivo real do cancelamento por operador (FR-008) — nunca mensagem genérica. */
    private String motivo;

    /** Preenchidos quando status = AGUARDANDO_TRANSFERENCIA (FR-009). */
    private String targetVesselId;
    private String transferAttemptId;
    private Instant transferOfferExpiresAt;

    public static String pkFor(String bookingId) {
        return "BOOKING#" + bookingId;
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

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI1")
    @DynamoDbAttribute("GSI1PK")
    @JsonIgnore
    public String getGsi1Pk() {
        return "BUYER#" + buyerId;
    }

    public void setGsi1Pk(String gsi1Pk) {
        // derivado de `buyerId`
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
