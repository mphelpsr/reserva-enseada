package com.empresa.vesselmanagement.application.event;

import java.time.LocalDate;

import com.empresa.vesselmanagement.domain.availability.TourType;

/** FR-007, Princípio VII: publicado quando uma embarcação da mesma frota tem vaga (T055). */
public record TransferViableEvent(String vesselId, LocalDate data, TourType tipoPasseio, String targetVesselId) {
}
