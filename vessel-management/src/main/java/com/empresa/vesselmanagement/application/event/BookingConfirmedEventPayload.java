package com.empresa.vesselmanagement.application.event;

/**
 * Contrato PROPOSTO por este módulo para o evento `booking.confirmed` (T059b) —
 * o módulo booking ainda não existe, então este payload precisa ser confirmado
 * junto da implementação de `tasks-booking.md` T053, no mesmo espírito da nota já
 * registrada para `booking.transferred`/`booking.cancelled` (T055/T059).
 */
public record BookingConfirmedEventPayload(String vesselId, String data, String tipoPasseio) {
}
