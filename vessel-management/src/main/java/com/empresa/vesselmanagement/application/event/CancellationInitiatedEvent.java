package com.empresa.vesselmanagement.application.event;

import java.time.LocalDate;

import com.empresa.vesselmanagement.domain.availability.TourType;

/** FR-007, Princípio VII: publicado quando nenhuma embarcação da frota tem vaga (T054). */
public record CancellationInitiatedEvent(String vesselId, LocalDate data, TourType tipoPasseio, String motivo) {
}
