package com.empresa.booking.application.event;

/**
 * `booking.transferred` — payload fechado em "Contrato da Saga" (plan.md).
 * `vesselId`/`data`/`tipoPasseio` são os da embarcação ORIGINAL (a que
 * recebeu `vessel.transfer.viable`), não os do destino; `transferAttemptId`
 * nunca é `null` aqui (todo `booking.transferred` é resposta a uma oferta).
 * Consumido pelo vessel-management (T059) — fecha a Saga de cancelamento.
 */
public record BookingTransferredEvent(
        String vesselId, String data, String tipoPasseio, String bookingId, String targetVesselId, String transferAttemptId) {
}
