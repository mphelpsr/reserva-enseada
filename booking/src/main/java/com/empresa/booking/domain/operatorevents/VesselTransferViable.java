package com.empresa.booking.domain.operatorevents;

/**
 * Modelo do evento `vessel.transfer.viable`, publicado pelo vessel-management
 * (T055) — payload fechado em "Contrato da Saga". Dispara FR-009: notifica o
 * comprador e aguarda confirmação por até 48h.
 *
 * `id` é o `BookingTransferAttempt.id` do lado vessel-management — precisa
 * ser persistido como `Booking.transferAttemptId` para ecoar de volta em
 * `booking.transferred`/`booking.cancelled` (T055 do lado deste módulo, T059
 * do lado vessel-management usa esse valor para localizar a tentativa certa
 * sem ambiguidade — ver o racional completo em "Contrato da Saga").
 */
public record VesselTransferViable(
        String id, String vesselId, String data, String tipoPasseio, String targetVesselId, String motivo) {
}
