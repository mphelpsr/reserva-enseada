package com.empresa.booking.domain.operatorevents;

/**
 * Modelo do evento `vessel.cancellation.operator-initiated`, publicado pelo
 * vessel-management (T054) — payload fechado em "Contrato da Saga". Dispara
 * FR-008: reembolso integral automático imediato, com o motivo REAL
 * comunicado ao comprador (Princípio VII — nunca mensagem genérica).
 */
public record OperatorInitiatedCancellation(String vesselId, String data, String tipoPasseio, String motivo) {
}
