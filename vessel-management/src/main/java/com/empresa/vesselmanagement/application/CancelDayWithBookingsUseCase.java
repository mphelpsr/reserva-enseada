package com.empresa.vesselmanagement.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.cancellation.BookingTransferAttempt;
import com.empresa.vesselmanagement.domain.cancellation.TransferAttemptStatus;
import com.empresa.vesselmanagement.domain.seatlimit.PlatformSeatLimit;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.infrastructure.dynamodb.AvailabilityRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingTransferAttemptRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.SeatLimitRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

import java.time.LocalDate;

/**
 * T046. FR-007, Princípio VII — Saga leve: busca embarcação da MESMA frota
 * (mesmo ownerId) com vaga disponível no dia/tipo de passeio; se achar, registra
 * a tentativa como VIABLE_PENDING e NÃO altera a disponibilidade original ainda
 * (aguarda confirmação do comprador — evento de retorno do booking, T059); senão,
 * registra CANCELLED_NO_ALTERNATIVE e aplica a indisponibilidade de imediato, sem
 * janela de análise.
 *
 * "Vaga disponível" aqui é o melhor proxy que este módulo tem sem acessar dados do
 * booking: dia marcado disponível + limite de vagas na plataforma > 0. A publicação
 * dos eventos SNS (`vessel.transfer.viable` / `vessel.cancellation.operator-initiated`)
 * é responsabilidade de T054/T055 (Fase 3.4), disparada a partir do resultado deste
 * caso de uso.
 */
@Service
public class CancelDayWithBookingsUseCase {

    private final VesselRepository vesselRepository;
    private final AvailabilityRepository availabilityRepository;
    private final SeatLimitRepository seatLimitRepository;
    private final BookingTransferAttemptRepository transferAttemptRepository;

    public CancelDayWithBookingsUseCase(
            VesselRepository vesselRepository,
            AvailabilityRepository availabilityRepository,
            SeatLimitRepository seatLimitRepository,
            BookingTransferAttemptRepository transferAttemptRepository) {
        this.vesselRepository = vesselRepository;
        this.availabilityRepository = availabilityRepository;
        this.seatLimitRepository = seatLimitRepository;
        this.transferAttemptRepository = transferAttemptRepository;
    }

    public BookingTransferAttempt cancelDay(String vesselId, LocalDate data, TourType tipoPasseio, String motivo) {
        Vessel vessel = vesselRepository.findById(vesselId).orElseThrow(() -> new VesselNotFoundException(vesselId));

        Optional<Vessel> alternative = vesselRepository.findByOwnerId(vessel.getOwnerId()).stream()
                .filter(candidate -> !candidate.getId().equals(vesselId))
                .filter(candidate -> temVagaDisponivel(candidate.getId(), data, tipoPasseio))
                .findFirst();

        BookingTransferAttempt attempt = BookingTransferAttempt.builder()
                .id(UUID.randomUUID().toString())
                .vesselId(vesselId)
                .data(data)
                .tipoPasseio(tipoPasseio)
                .motivo(motivo)
                .targetVesselId(alternative.map(Vessel::getId).orElse(null))
                .status(alternative.isPresent()
                        ? TransferAttemptStatus.VIABLE_PENDING
                        : TransferAttemptStatus.CANCELLED_NO_ALTERNATIVE)
                .build();

        transferAttemptRepository.save(attempt);

        if (alternative.isEmpty()) {
            availabilityRepository.save(DeclaredAvailability.builder()
                    .vesselId(vesselId)
                    .data(data)
                    .tipoPasseio(tipoPasseio)
                    .disponivel(false)
                    .motivo(motivo)
                    .build());
        }

        return attempt;
    }

    private boolean temVagaDisponivel(String vesselId, LocalDate data, TourType tipoPasseio) {
        boolean disponivel = availabilityRepository.findByVesselDateType(vesselId, data, tipoPasseio)
                .map(DeclaredAvailability::isDisponivel)
                .orElse(false);
        if (!disponivel) {
            return false;
        }
        int limite = seatLimitRepository.findByVesselDateType(vesselId, data, tipoPasseio)
                .map(PlatformSeatLimit::getLimite)
                .orElse(0);
        return limite > 0;
    }
}
