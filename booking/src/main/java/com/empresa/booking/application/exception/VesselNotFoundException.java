package com.empresa.booking.application.exception;

/**
 * FR-001/FR-002: nenhum `SeatCount` foi replicado ainda para esta embarcação —
 * ou seja, este módulo nunca recebeu `vessel.availability.changed`/
 * `vessel.seatlimit.changed` para ela, o sinal disponível de "a embarcação
 * existe" (ver GetVesselCalendarReadModelUseCase).
 */
public class VesselNotFoundException extends RuntimeException {

    public VesselNotFoundException(String vesselId) {
        super("Embarcação não encontrada: " + vesselId);
    }
}
