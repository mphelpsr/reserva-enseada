package com.empresa.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.domain.operatorevents.OperatorInitiatedCancellation;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;
import com.empresa.booking.infrastructure.messaging.SesEmailNotifier;

/** T042 — FR-008: reembolso integral automático imediato por cancelamento do proprietário. */
@ExtendWith(MockitoExtension.class)
class ProcessOperatorCancellationUseCaseTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatCountRepository seatCountRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SesEmailNotifier emailNotifier;

    private Booking booking(String id, BookingStatus status) {
        return Booking.builder()
                .id(id).buyerId("buyer-1").vesselId("vessel-1")
                .data(LocalDate.of(2026, 12, 20)).tipoPasseio(TourType.ALTO_MAR).quantidade(2)
                .status(status).build();
    }

    @Test
    void deveReembolsarTodasAsReservasAtivasComMotivoReal() {
        Booking confirmada = booking("booking-1", BookingStatus.CONFIRMADA);
        Booking aguardandoTransferencia = booking("booking-2", BookingStatus.AGUARDANDO_TRANSFERENCIA);
        Booking jaCancelada = booking("booking-3", BookingStatus.CANCELADA);
        when(bookingRepository.findByVesselDateAndType("vessel-1", LocalDate.of(2026, 12, 20), TourType.ALTO_MAR))
                .thenReturn(List.of(confirmada, aguardandoTransferencia, jaCancelada));

        ProcessOperatorCancellationUseCase useCase =
                new ProcessOperatorCancellationUseCase(bookingRepository, seatCountRepository, eventPublisher, emailNotifier);
        useCase.process(new OperatorInitiatedCancellation("vessel-1", "2026-12-20", "alto_mar", "avaria no motor"));

        assertThat(confirmada.getStatus()).isEqualTo(BookingStatus.REEMBOLSADA);
        assertThat(confirmada.getMotivo()).isEqualTo("avaria no motor");
        assertThat(aguardandoTransferencia.getStatus()).isEqualTo(BookingStatus.REEMBOLSADA);
        assertThat(jaCancelada.getStatus()).isEqualTo(BookingStatus.CANCELADA);

        verify(bookingRepository).save(confirmada);
        verify(bookingRepository).save(aguardandoTransferencia);
        verify(bookingRepository, never()).save(jaCancelada);
        verify(seatCountRepository, org.mockito.Mockito.times(2))
                .decrementSold("vessel-1", LocalDate.of(2026, 12, 20), TourType.ALTO_MAR, 2);
    }

    @Test
    void deveIgnorarReservasQueNaoEstaoMaisAtivas() {
        Booking transferida = booking("booking-1", BookingStatus.TRANSFERIDA);
        Booking reembolsada = booking("booking-2", BookingStatus.REEMBOLSADA);
        when(bookingRepository.findByVesselDateAndType("vessel-1", LocalDate.of(2026, 12, 20), TourType.ALTO_MAR))
                .thenReturn(List.of(transferida, reembolsada));

        ProcessOperatorCancellationUseCase useCase =
                new ProcessOperatorCancellationUseCase(bookingRepository, seatCountRepository, eventPublisher, emailNotifier);
        useCase.process(new OperatorInitiatedCancellation("vessel-1", "2026-12-20", "alto_mar", "avaria no motor"));

        verify(bookingRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(seatCountRepository, never()).decrementSold(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
