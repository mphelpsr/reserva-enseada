package com.empresa.vesselmanagement.application.event;

/**
 * Contrato de `booking.transferred` (T059), payload confirmado em
 * 2026-07-12 — ver "Contrato da Saga" em plan.md. `vesselId`/`data`/
 * `tipoPasseio` são os da embarcação ORIGINAL (a que recebeu
 * `vessel.transfer.viable`), não os do destino; `transferAttemptId` nunca é
 * `null` aqui (todo `booking.transferred` é resposta a uma oferta).
 */
public record BookingTransferredEventPayload(
        String vesselId, String data, String tipoPasseio, String bookingId, String targetVesselId, String transferAttemptId) {
}
