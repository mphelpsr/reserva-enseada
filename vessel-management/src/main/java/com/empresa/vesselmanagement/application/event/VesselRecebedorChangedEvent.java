package com.empresa.vesselmanagement.application.event;

/**
 * T059c — publicado uma vez por embarcação do proprietário quando a chave Pix
 * (`payment_recebedor_id`) muda. `pixKey`, não `recebedorId` — modelo de repasse via
 * split instantâneo Pix (Transfeera/OpenPix), não mais subconta de gateway (ver
 * FR-016 em spec.md, revisão de 2026-07-12).
 */
public record VesselRecebedorChangedEvent(String vesselId, String pixKey) {
}
