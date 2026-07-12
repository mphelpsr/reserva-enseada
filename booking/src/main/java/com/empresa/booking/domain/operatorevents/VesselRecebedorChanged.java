package com.empresa.booking.domain.operatorevents;

/**
 * Modelo do evento `vessel.recebedor.changed` — payload fechado em "Contrato
 * da Saga" (plan-vessel-management.md/plan-booking.md). `pixKey`, não
 * `recebedorId` — modelo de repasse via split instantâneo Pix
 * (Transfeera/OpenPix), não mais subconta de gateway (revisão de 2026-07-12).
 * Publicado do lado vessel-management por T059c.
 */
public record VesselRecebedorChanged(String vesselId, String pixKey) {
}
