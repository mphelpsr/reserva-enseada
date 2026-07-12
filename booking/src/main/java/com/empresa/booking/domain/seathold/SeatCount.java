package com.empresa.booking.domain.seathold;

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
 * VESSEL#{vesselId} / SEATCOUNT#{data}#{tipoPasseio} (plan.md — Phase 1, Data
 * Model). Réplica local do limite de vagas (`vessel.seatlimit.changed`) e
 * contadores `sold`/`held` mantidos pelas escritas condicionais deste módulo
 * (FR-003). `disponivel`/`motivo` também são réplica local, mantida via
 * `vessel.availability.changed` — reaproveita o MESMO item em vez de um novo
 * tipo (Princípio VI): "vagas restantes" e "está disponível" são as duas
 * faces do mesmo access pattern (consultar o dia/tipo de passeio de uma
 * embarcação), plan.md não lista um item de disponibilidade separado.
 */
@DynamoDbBean
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatCount {

    private String vesselId;
    private LocalDate data;

    @Getter(onMethod_ = @DynamoDbConvertedBy(TourTypeConverter.class))
    private TourType tipoPasseio;

    private int limite;
    private int sold;
    private int held;
    private boolean disponivel;
    private String motivo;

    public static String skFor(LocalDate data, TourType tipoPasseio) {
        return "SEATCOUNT#" + data + "#" + tipoPasseio.getValue();
    }

    /** FR-013, Opção C: nunca negativo, independente de quanto já foi vendido/retido. */
    public int vagasRestantes() {
        return Math.max(0, limite - sold - held);
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
        return skFor(data, tipoPasseio);
    }

    public void setSk(String sk) {
        // derivado de `data`/`tipoPasseio`
    }
}
