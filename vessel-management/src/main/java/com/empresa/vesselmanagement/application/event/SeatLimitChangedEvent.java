package com.empresa.vesselmanagement.application.event;

import java.time.LocalDate;

import com.empresa.vesselmanagement.domain.availability.TourType;

/** FR-015: publicado a cada definição de limite de vagas (T053) — booking recalcula vagas restantes. */
public record SeatLimitChangedEvent(String vesselId, LocalDate data, TourType tipoPasseio, int limite) {
}
