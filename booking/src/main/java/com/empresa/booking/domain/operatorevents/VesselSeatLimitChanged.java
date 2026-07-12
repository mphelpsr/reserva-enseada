package com.empresa.booking.domain.operatorevents;

/**
 * Modelo do evento `vessel.seatlimit.changed`, publicado pelo
 * vessel-management (T053) — payload fechado em "Contrato da Saga". Atualiza
 * `SeatCount.limite` (FR-013, Opção C: sempre aceita, nunca fica negativo,
 * nunca invalida reservas já confirmadas).
 */
public record VesselSeatLimitChanged(String vesselId, String data, String tipoPasseio, int limite) {
}
