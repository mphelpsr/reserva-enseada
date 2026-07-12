package com.empresa.vesselmanagement.application.event;

import java.time.LocalDate;

import com.empresa.vesselmanagement.domain.availability.TourType;

/** FR-005: publicado sempre que a disponibilidade declarada de uma embarcação é alterada (T052). */
public record AvailabilityChangedEvent(String vesselId, LocalDate data, TourType tipoPasseio, boolean disponivel, String motivo) {
}
