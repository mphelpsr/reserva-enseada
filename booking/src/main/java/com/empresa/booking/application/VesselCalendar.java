package com.empresa.booking.application;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VesselCalendar(String vesselId, List<Dia> dias) {

    /**
     * Chaves JSON em snake_case (`alto_mar`/`orla`) para bater com a mesma
     * convenção de `TourType.getValue()` usada em todo o resto da API
     * (request bodies, eventos) — sem os `@JsonProperty` aqui, Jackson usaria
     * o nome Java do record component (`altoMar`), quebrando o contrato.
     */
    public record Dia(
            LocalDate data,
            @JsonProperty("alto_mar") DiaTipoPasseio altoMar,
            @JsonProperty("orla") DiaTipoPasseio orla) {
    }

    /** FR-002: `motivo` aqui é o indicador de maré/previsão — apoio, nunca oculta uma data disponível. */
    public record DiaTipoPasseio(boolean disponivel, int vagasRestantes, String motivo) {
    }
}
