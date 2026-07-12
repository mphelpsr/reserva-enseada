package com.empresa.vesselmanagement.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.empresa.vesselmanagement.application.exception.IncompatibleTargetVesselException;
import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.domain.bookingcount.ConfirmedBookingCount;
import com.empresa.vesselmanagement.domain.cancellation.VesselTransfer;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingCountRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselTransferRepository;

/**
 * T040. FR-002: exige transferência antes de remoção com reservas futuras — move as
 * reservas para outra embarcação COMPATÍVEL (mesma capacidade mínima e porto de
 * saída).
 *
 * Limite conhecido: este módulo não é dono do dado de reservas (só a réplica
 * `ConfirmedBookingCount`, ver plan.md — "Eventos Consumidos") e a Saga de
 * transferência efetiva entre módulos não está especificada além do escopo deste
 * plan — este caso de uso registra a intenção (`VesselTransfer`) e reporta a
 * contagem, mas a movimentação real das reservas no módulo booking é responsabilidade
 * dele (evento a definir, fora do escopo de T040/T040b).
 */
@Service
public class TransferVesselUseCase {

    private final VesselRepository vesselRepository;
    private final BookingCountRepository bookingCountRepository;
    private final VesselTransferRepository vesselTransferRepository;

    public TransferVesselUseCase(
            VesselRepository vesselRepository,
            BookingCountRepository bookingCountRepository,
            VesselTransferRepository vesselTransferRepository) {
        this.vesselRepository = vesselRepository;
        this.bookingCountRepository = bookingCountRepository;
        this.vesselTransferRepository = vesselTransferRepository;
    }

    public TransferVesselResult transfer(String vesselId, String targetVesselId) {
        Vessel source = vesselRepository.findById(vesselId).orElseThrow(() -> new VesselNotFoundException(vesselId));
        Vessel target = vesselRepository.findById(targetVesselId)
                .orElseThrow(() -> new IncompatibleTargetVesselException(targetVesselId));

        if (target.getCapacidadeMaxima() < source.getCapacidadeMaxima()
                || !target.getPortoSaida().equals(source.getPortoSaida())) {
            throw new IncompatibleTargetVesselException(targetVesselId);
        }

        List<ConfirmedBookingCount> futureBookings =
                bookingCountRepository.findFutureBookingsForVessel(vesselId, LocalDate.now());
        int transferredCount = futureBookings.stream().mapToInt(ConfirmedBookingCount::getCount).sum();

        vesselTransferRepository.save(VesselTransfer.builder()
                .id(UUID.randomUUID().toString())
                .vesselId(vesselId)
                .targetVesselId(targetVesselId)
                .transferredBookingsCount(transferredCount)
                .transferredAt(Instant.now())
                .build());

        return new TransferVesselResult(targetVesselId, transferredCount);
    }
}
