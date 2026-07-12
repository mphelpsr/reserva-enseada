package com.empresa.booking.domain.operatorevents;

/**
 * Modelo do evento `vessel.recebedor.changed` — payload fechado em "Contrato
 * da Saga" (plan-vessel-management.md/plan-booking.md, decisão de 2026-07-12).
 * Ainda não publicado do lado vessel-management (T059c, bloqueada por não
 * existir lá um ponto de escrita para `payment_recebedor_id`); este
 * consumidor já fica pronto para quando isso existir.
 */
public record VesselRecebedorChanged(String vesselId, String recebedorId) {
}
