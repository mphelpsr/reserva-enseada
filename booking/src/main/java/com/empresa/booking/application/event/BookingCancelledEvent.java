package com.empresa.booking.application.event;

/**
 * `booking.cancelled` — payload fechado em "Contrato da Saga" (plan.md).
 * `transferAttemptId` é o `id` que veio de `vessel.transfer.viable` quando
 * este cancelamento resolve (recusa ou expiração das 48h) uma oferta de
 * transferência pendente; `null` em desistência direta do comprador ou
 * cancelamento por iniciativa do operador sem oferta pendente envolvida.
 * Consumido pelo vessel-management (T059).
 */
public record BookingCancelledEvent(String vesselId, String data, String tipoPasseio, String bookingId, String transferAttemptId) {
}
