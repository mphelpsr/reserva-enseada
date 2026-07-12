package com.empresa.booking.application.event;

/**
 * `booking.confirmed` — payload fechado em "Contrato da Saga" (plan.md).
 * Consumido pelo vessel-management (T059b) para manter `ConfirmedBookingCount`.
 */
public record BookingConfirmedEvent(String vesselId, String data, String tipoPasseio) {
}
