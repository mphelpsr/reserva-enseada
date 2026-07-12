package com.empresa.booking.application;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.empresa.booking.application.exception.InsufficientSeatsException;
import com.empresa.booking.application.exception.MinimumAdvancePurchaseException;
import com.empresa.booking.domain.seathold.SeatHold;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatHoldRepository;

/**
 * T038. FR-003 (overselling), FR-004 (retenção de 10 min), FR-014
 * (antecedência mínima de 24h).
 *
 * `precoPorVagaCentavos` é um placeholder de configuração — nem spec.md nem
 * plan.md modelam preço por embarcação/tipo de passeio ainda (nenhuma
 * entidade de "tabela de preços" existe hoje). Trocar por uma consulta real
 * assim que esse conceito for modelado; não bloqueia o resto da Fase 3.3
 * porque é local a este caso de uso, sem implicação cross-módulo (diferente
 * do gap do `payment_recebedor_id`, que exigiu revisão do contrato da Saga).
 */
@Service
public class CreateHoldUseCase {

    private static final int HOLD_DURATION_MINUTES = 10;
    private static final int MINIMUM_ADVANCE_HOURS = 24;

    private final SeatCountRepository seatCountRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final long precoPorVagaCentavos;

    public CreateHoldUseCase(
            SeatCountRepository seatCountRepository,
            SeatHoldRepository seatHoldRepository,
            @Value("${app.booking.preco-por-vaga-centavos:15000}") long precoPorVagaCentavos) {
        this.seatCountRepository = seatCountRepository;
        this.seatHoldRepository = seatHoldRepository;
        this.precoPorVagaCentavos = precoPorVagaCentavos;
    }

    public CreateHoldResult createHold(CreateHoldCommand command) {
        Instant agora = Instant.now();
        Instant inicioDoPasseio = command.data().atStartOfDay(ZoneOffset.UTC).toInstant();
        if (Duration.between(agora, inicioDoPasseio).toHours() < MINIMUM_ADVANCE_HOURS) {
            throw new MinimumAdvancePurchaseException(command.vesselId(), command.data().toString());
        }

        reapExpiredHolds(command, agora);

        boolean incrementado = seatCountRepository.tryIncrementHeld(
                command.vesselId(), command.data(), command.tipoPasseio(), command.quantidade());
        if (!incrementado) {
            throw new InsufficientSeatsException(command.vesselId(), command.data().toString(), command.tipoPasseio().getValue());
        }

        String holdId = UUID.randomUUID().toString();
        Instant expiresAt = agora.plusSeconds(HOLD_DURATION_MINUTES * 60L);

        try {
            seatHoldRepository.save(SeatHold.builder()
                    .id(holdId)
                    .buyerId(command.buyerId())
                    .vesselId(command.vesselId())
                    .data(command.data())
                    .tipoPasseio(command.tipoPasseio())
                    .quantidade(command.quantidade())
                    .expiresAt(expiresAt)
                    .valorTotalCentavos(precoPorVagaCentavos * command.quantidade())
                    .build());
        } catch (RuntimeException e) {
            // compensação best-effort: sem o HOLD, a vaga não pode ficar retida
            seatCountRepository.decrementHeld(command.vesselId(), command.data(), command.tipoPasseio(), command.quantidade());
            throw e;
        }

        return new CreateHoldResult(holdId, expiresAt, command.vesselId(), command.quantidade());
    }

    /**
     * FR-004: hold expirado nunca deve bloquear uma vaga nova, independente
     * do sweeper (T046, a cada 1 min) já ter passado por ele — sem isto,
     * `vagasDisponiveis` ficaria presa até o próximo ciclo do sweeper.
     */
    private void reapExpiredHolds(CreateHoldCommand command, Instant agora) {
        List<SeatHold> expirados = seatHoldRepository.findExpiredByVesselDateType(
                command.vesselId(), command.data(), command.tipoPasseio(), agora);
        for (SeatHold expirado : expirados) {
            seatCountRepository.decrementHeld(command.vesselId(), command.data(), command.tipoPasseio(), expirado.getQuantidade());
            seatHoldRepository.delete(expirado.getId());
        }
    }
}
