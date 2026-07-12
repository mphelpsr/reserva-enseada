package com.empresa.booking.domain.operatorevents;

/**
 * Modelo do evento `vessel.availability.changed`, publicado pelo
 * vessel-management (T052) — payload fechado em "Contrato da Saga"
 * (plan-vessel-management.md). Atualiza o read-model de disponibilidade
 * exibido em GET /vessels/{id}/calendar (T045).
 */
public record VesselAvailabilityChanged(String vesselId, String data, String tipoPasseio, boolean disponivel, String motivo) {
}
