package com.empresa.vesselmanagement.application.event;

/**
 * Contrato de `booking.cancelled` (T059), payload confirmado em 2026-07-12 —
 * ver "Contrato da Saga" em plan.md. `transferAttemptId` é `null` em
 * desistência direta do comprador (FR-006, sem transferência envolvida) ou
 * preenchido quando este cancelamento resolve (recusa ou expiração das 48h)
 * uma oferta de transferência pendente (`TRANSFER#<transferAttemptId>`).
 */
public record BookingCancelledEventPayload(String vesselId, String data, String tipoPasseio, String bookingId, String transferAttemptId) {
}
