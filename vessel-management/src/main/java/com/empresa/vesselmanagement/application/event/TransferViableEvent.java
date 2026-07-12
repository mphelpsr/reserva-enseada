package com.empresa.vesselmanagement.application.event;

import java.time.LocalDate;

import com.empresa.vesselmanagement.domain.availability.TourType;

/**
 * FR-007, Princípio VII: publicado quando uma embarcação da mesma frota tem vaga (T055).
 *
 * `id` é o mesmo `BookingTransferAttempt.id` (VESSEL#vesselId/TRANSFER#id) — contrato
 * revisado em 2026-07-12 junto com a Fase 3.1 do booking (ver "Contrato da Saga" em
 * plan.md): o booking precisa ecoar esse id de volta em `booking.transferred`/
 * `booking.cancelled` (campo `transferAttemptId`) para o futuro T059 localizar e
 * fechar a tentativa certa por GetItem direto — sem ele, a única correlação possível
 * seria por (vesselId, data, tipoPasseio), que não é única: uma segunda tentativa de
 * cancelamento do mesmo dia/tipo enquanto a primeira ainda está VIABLE_PENDING criaria
 * um segundo registro ambíguo.
 *
 * `motivo` também foi adicionado nesta revisão — faltava (só existia em
 * CancellationInitiatedEvent), e o comprador precisa do motivo real na notificação de
 * transferência tanto quanto na de cancelamento (mesma exigência de "nunca mensagem
 * genérica" já aplicada ao FR-008 do booking).
 */
public record TransferViableEvent(
        String id, String vesselId, LocalDate data, TourType tipoPasseio, String targetVesselId, String motivo) {
}
