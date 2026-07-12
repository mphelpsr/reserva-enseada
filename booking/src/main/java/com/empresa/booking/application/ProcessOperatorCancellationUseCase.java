package com.empresa.booking.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.empresa.booking.application.event.BookingCancelledEvent;
import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.domain.operatorevents.OperatorInitiatedCancellation;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;
import com.empresa.booking.infrastructure.messaging.SesEmailNotifier;

/**
 * T042. Consumidor de `vessel.cancellation.operator-initiated` (FR-008,
 * cenário 6): reembolso integral automático imediato para TODAS as reservas
 * ativas daquele dia/tipo de passeio/embarcação, com o motivo REAL do
 * proprietário (Princípio VII — nunca mensagem genérica).
 *
 * Só reservas ainda ATIVAS (CONFIRMADA/AGUARDANDO_TRANSFERENCIA) são
 * afetadas — uma reserva já cancelada/reembolsada/transferida por outro
 * caminho é ignorada (idempotência e respeito à prioridade do FR-009: se o
 * comprador já tinha cancelado, esse cancelamento por operador não faz mais
 * sentido pra aquela reserva específica).
 */
@Service
public class ProcessOperatorCancellationUseCase {

    private static final Set<BookingStatus> ATIVAS = Set.of(BookingStatus.CONFIRMADA, BookingStatus.AGUARDANDO_TRANSFERENCIA);

    private final BookingRepository bookingRepository;
    private final SeatCountRepository seatCountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SesEmailNotifier emailNotifier;

    public ProcessOperatorCancellationUseCase(
            BookingRepository bookingRepository,
            SeatCountRepository seatCountRepository,
            ApplicationEventPublisher eventPublisher,
            SesEmailNotifier emailNotifier) {
        this.bookingRepository = bookingRepository;
        this.seatCountRepository = seatCountRepository;
        this.eventPublisher = eventPublisher;
        this.emailNotifier = emailNotifier;
    }

    public void process(OperatorInitiatedCancellation event) {
        LocalDate data = LocalDate.parse(event.data());
        TourType tipoPasseio = TourType.fromValue(event.tipoPasseio());

        List<Booking> afetadas = bookingRepository.findByVesselDateAndType(event.vesselId(), data, tipoPasseio);

        for (Booking booking : afetadas) {
            if (!ATIVAS.contains(booking.getStatus())) {
                continue;
            }

            booking.setStatus(BookingStatus.REEMBOLSADA);
            booking.setMotivo(event.motivo());
            bookingRepository.save(booking);
            seatCountRepository.decrementSold(event.vesselId(), data, tipoPasseio, booking.getQuantidade());

            eventPublisher.publishEvent(new BookingCancelledEvent(
                    event.vesselId(), event.data(), event.tipoPasseio(), booking.getId(), booking.getTransferAttemptId()));
            emailNotifier.notifyCancelled(booking, event.motivo());
        }
    }
}
