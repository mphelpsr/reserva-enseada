package com.empresa.booking.domain.booking;

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
 * VESSEL#{vesselId} / BOOKING#{data}#{bookingId} (plan.md — Phase 1, Data
 * Model, access pattern 6). Item ponteiro, escrito por BookingRepository na
 * MESMA transação do item BOOKING#{id}/METADATA (Booking.java) — suporta
 * "localizar reservas afetadas por cancelamento/transferência de um dia"
 * (FR-008, FR-009), que o GSI1 (indexado por comprador) não cobre.
 *
 * `tipoPasseio` fica como atributo comum (fora da SK, que só tem a data) —
 * o Query retorna todas as reservas do dia (os dois tipos de passeio), e o
 * caso de uso filtra por tipoPasseio em memória (Princípio VI: mais simples
 * que uma SK composta com tipoPasseio embutido).
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingVesselDateIndex {

    private String vesselId;
    private LocalDate data;
    private String bookingId;

    @Getter(onMethod_ = @DynamoDbConvertedBy(TourTypeConverter.class))
    private TourType tipoPasseio;

    public static String skFor(LocalDate data, String bookingId) {
        return "BOOKING#" + data + "#" + bookingId;
    }

    public static String skPrefixFor(LocalDate data) {
        return "BOOKING#" + data;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    @JsonIgnore
    public String getPk() {
        return "VESSEL#" + vesselId;
    }

    public void setPk(String pk) {
        // derivado de `vesselId`
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    @JsonIgnore
    public String getSk() {
        return skFor(data, bookingId);
    }

    public void setSk(String sk) {
        // derivado de `data`/`bookingId`
    }
}
