package com.empresa.booking.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.empresa.booking.application.event.BookingCancelledEvent;
import com.empresa.booking.application.event.BookingTransferredEvent;
import com.empresa.booking.application.exception.BookingNotFoundException;
import com.empresa.booking.application.exception.NoPendingTransferOfferException;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;
import com.empresa.booking.infrastructure.messaging.SesEmailNotifier;

/**
 * T041. FR-009: resposta do comprador a uma oferta de transferência pendente
 * (`AGUARDANDO_TRANSFERENCIA`, criada por ProcessTransferOfferUseCase, T043).
 * Aceitar move a reserva pra nova embarcação/dia (atualiza `sold` nas DUAS
 * pontas — origem perde, destino ganha); recusar dispara reembolso integral
 * (mesmo efeito de FR-006, sem checar CancellationPolicy — a recusa aqui é
 * sempre aceita: foi o proprietário quem iniciou a mudança, não o comprador).
 */
@Service
public class RespondToTransferUseCase {

    private final BookingRepository bookingRepository;
    private final SeatCountRepository seatCountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SesEmailNotifier emailNotifier;

    public RespondToTransferUseCase(
            BookingRepository bookingRepository,
            SeatCountRepository seatCountRepository,
            ApplicationEventPublisher eventPublisher,
            SesEmailNotifier emailNotifier) {
        this.bookingRepository = bookingRepository;
        this.seatCountRepository = seatCountRepository;
        this.eventPublisher = eventPublisher;
        this.emailNotifier = emailNotifier;
    }

    public Booking respond(String bookingId, boolean aceitar) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (booking.getStatus() != BookingStatus.AGUARDANDO_TRANSFERENCIA) {
            throw new NoPendingTransferOfferException(bookingId);
        }

        String vesselIdOrigem = booking.getVesselId();

        if (aceitar) {
            seatCountRepository.decrementSold(vesselIdOrigem, booking.getData(), booking.getTipoPasseio(), booking.getQuantidade());

            String vesselIdDestino = booking.getTargetVesselId();
            booking.setVesselId(vesselIdDestino);
            booking.setStatus(BookingStatus.TRANSFERIDA);
            booking.setTargetVesselId(null);
            booking.setTransferOfferExpiresAt(null);

            bookingRepository.moveToVessel(booking, vesselIdOrigem);
            seatCountRepository.incrementSold(vesselIdDestino, booking.getData(), booking.getTipoPasseio(), booking.getQuantidade());

            eventPublisher.publishEvent(new BookingTransferredEvent(
                    vesselIdOrigem, booking.getData().toString(), booking.getTipoPasseio().getValue(),
                    booking.getId(), vesselIdDestino, booking.getTransferAttemptId()));
            emailNotifier.notifyTransferred(booking, vesselIdDestino);
        } else {
            seatCountRepository.decrementSold(vesselIdOrigem, booking.getData(), booking.getTipoPasseio(), booking.getQuantidade());

            eventPublisher.publishEvent(new BookingCancelledEvent(
                    vesselIdOrigem, booking.getData().toString(), booking.getTipoPasseio().getValue(),
                    booking.getId(), booking.getTransferAttemptId()));
            emailNotifier.notifyCancelled(booking, null);

            booking.setStatus(BookingStatus.REEMBOLSADA);
            booking.setTargetVesselId(null);
            booking.setTransferOfferExpiresAt(null);
            bookingRepository.save(booking);
        }

        return booking;
    }
}
