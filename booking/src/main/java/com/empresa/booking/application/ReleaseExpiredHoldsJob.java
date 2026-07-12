package com.empresa.booking.application;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.domain.seathold.SeatHold;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatHoldRepository;

/**
 * T046. Job periódico (EventBridge Scheduler a cada 1 min — infra/eventbridge.tf),
 * bean `releaseExpiredHoldsJob` (nome padrão do Spring para @Component, sem
 * precisar de @Bean explícito). Dupla responsabilidade — reaproveita o mesmo
 * mecanismo periódico para dois tipos de "arrumar a casa" que o TTL nativo do
 * DynamoDB não garante a tempo (CLAUDE.md, ressalva do hold de 10 minutos; a
 * mesma lógica se aplica à janela de 48h da oferta de transferência, FR-009):
 *
 * 1. Holds expirados (FR-004): devolve `held` e remove o item.
 * 2. Ofertas de transferência sem resposta em 48h (FR-009): cancela a
 *    reserva com reembolso integral e libera `sold` na embarcação de
 *    origem — sem passar por CancelBookingByBuyerUseCase porque aqui a
 *    iniciativa não é do comprador, e sim da ausência de resposta dele.
 */
@Component
public class ReleaseExpiredHoldsJob implements Runnable {

    private final SeatHoldRepository seatHoldRepository;
    private final SeatCountRepository seatCountRepository;
    private final BookingRepository bookingRepository;

    public ReleaseExpiredHoldsJob(
            SeatHoldRepository seatHoldRepository, SeatCountRepository seatCountRepository, BookingRepository bookingRepository) {
        this.seatHoldRepository = seatHoldRepository;
        this.seatCountRepository = seatCountRepository;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public void run() {
        releaseExpiredHolds();
        cancelExpiredTransferOffers();
    }

    private void releaseExpiredHolds() {
        Instant now = Instant.now();
        for (SeatHold hold : seatHoldRepository.findAll()) {
            if (hold.isExpired(now)) {
                seatCountRepository.decrementHeld(hold.getVesselId(), hold.getData(), hold.getTipoPasseio(), hold.getQuantidade());
                seatHoldRepository.delete(hold.getId());
            }
        }
    }

    private void cancelExpiredTransferOffers() {
        List<Booking> expiradas = bookingRepository.findAwaitingTransferExpiredBefore(Instant.now());
        for (Booking booking : expiradas) {
            if (booking.getData() != null && booking.getTipoPasseio() != null) {
                seatCountRepository.decrementSold(
                        booking.getVesselId(), booking.getData(), booking.getTipoPasseio(), booking.getQuantidade());
            }
            booking.setStatus(BookingStatus.REEMBOLSADA);
            booking.setTargetVesselId(null);
            booking.setTransferAttemptId(null);
            booking.setTransferOfferExpiresAt(null);
            bookingRepository.save(booking);
        }
    }
}
