package com.empresa.booking.application;

import java.time.Instant;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.empresa.booking.application.event.BookingCancelledEvent;
import com.empresa.booking.application.exception.BookingNotFoundException;
import com.empresa.booking.application.exception.CancellationWindowExpiredException;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.domain.cancellation.CancellationPolicy;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;
import com.empresa.booking.infrastructure.messaging.SesEmailNotifier;

/**
 * T040. FR-006/FR-007: cancelamento por desistência do comprador — modelo
 * binário (CancellationPolicy), sem escalonamento. Também é o lado
 * "comprador" da corrida de prioridade do FR-009 (T024): permitido mesmo com
 * uma oferta de transferência pendente (AGUARDANDO_TRANSFERENCIA) — o
 * cancelamento do comprador sempre pode interromper essa espera.
 */
@Service
public class CancelBookingByBuyerUseCase {

    private static final Set<BookingStatus> CANCELAVEIS = Set.of(BookingStatus.CONFIRMADA, BookingStatus.AGUARDANDO_TRANSFERENCIA);

    private final BookingRepository bookingRepository;
    private final SeatCountRepository seatCountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SesEmailNotifier emailNotifier;

    public CancelBookingByBuyerUseCase(
            BookingRepository bookingRepository,
            SeatCountRepository seatCountRepository,
            ApplicationEventPublisher eventPublisher,
            SesEmailNotifier emailNotifier) {
        this.bookingRepository = bookingRepository;
        this.seatCountRepository = seatCountRepository;
        this.eventPublisher = eventPublisher;
        this.emailNotifier = emailNotifier;
    }

    public CancelBookingResult cancel(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!CANCELAVEIS.contains(booking.getStatus())) {
            throw new CancellationWindowExpiredException(bookingId);
        }

        if (!CancellationPolicy.dentroDaJanela(booking.getCompradaEm(), booking.getData(), Instant.now())) {
            throw new CancellationWindowExpiredException(bookingId);
        }

        booking.setStatus(BookingStatus.REEMBOLSADA);
        bookingRepository.save(booking);
        seatCountRepository.decrementSold(booking.getVesselId(), booking.getData(), booking.getTipoPasseio(), booking.getQuantidade());

        eventPublisher.publishEvent(new BookingCancelledEvent(
                booking.getVesselId(), booking.getData().toString(), booking.getTipoPasseio().getValue(),
                booking.getId(), booking.getTransferAttemptId()));
        emailNotifier.notifyCancelled(booking, null);

        return new CancelBookingResult(BookingStatus.REEMBOLSADA, true);
    }
}
