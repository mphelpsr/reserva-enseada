package com.empresa.vesselmanagement.application;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** T044: visão consolidada do calendário de uma embarcação para o painel desktop. */
public record VesselCalendar(String vesselId, List<CalendarDay> dias) {

    public record CalendarDay(
            LocalDate data,
            @JsonProperty("alto_mar") TourAvailability altoMar,
            @JsonProperty("orla") TourAvailability orla) {
    }

    /** `disponivel` já reflete o efeito do rodízio (FR-013/FR-014) sobre Alto Mar. */
    public record TourAvailability(boolean disponivel, String motivo) {
    }
}
