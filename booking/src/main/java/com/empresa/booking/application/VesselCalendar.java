package com.empresa.booking.application;

import java.time.LocalDate;
import java.util.List;

public record VesselCalendar(String vesselId, List<Dia> dias) {

    public record Dia(LocalDate data, DiaTipoPasseio altoMar, DiaTipoPasseio orla) {
    }

    /** FR-002: `motivo` aqui é o indicador de maré/previsão — apoio, nunca oculta uma data disponível. */
    public record DiaTipoPasseio(boolean disponivel, int vagasRestantes, String motivo) {
    }
}
