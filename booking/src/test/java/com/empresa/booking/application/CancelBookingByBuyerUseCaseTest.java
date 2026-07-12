package com.empresa.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.empresa.booking.application.exception.BookingNotFoundException;
import com.empresa.booking.application.exception.CancellationWindowExpiredException;
import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;

/** T040 — FR-006/FR-007: cancelamento binário por desistência do comprador. */
@ExtendWith(MockitoExtension.class)
class CancelBookingByBuyerUseCaseTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatCountRepository seatCountRepository;

    private Booking bookingConfirmada(Instant compradaEm, LocalDate data) {
        return Booking.builder()
                .id("booking-1").buyerId("buyer-1").vesselId("vessel-1")
                .data(data).tipoPasseio(TourType.ALTO_MAR).quantidade(1)
                .status(BookingStatus.CONFIRMADA).compradaEm(compradaEm).build();
    }

    @Test
    void deveReembolsarIntegralmenteDentroDaJanela() {
        Booking booking = bookingConfirmada(Instant.now(), LocalDate.now().plusDays(30));
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        CancelBookingByBuyerUseCase useCase = new CancelBookingByBuyerUseCase(bookingRepository, seatCountRepository);
        CancelBookingResult result = useCase.cancel("booking-1");

        assertThat(result.status()).isEqualTo(BookingStatus.REEMBOLSADA);
        assertThat(result.reembolsoIntegral()).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REEMBOLSADA);
        verify(seatCountRepository).decrementSold("vessel-1", booking.getData(), TourType.ALTO_MAR, 1);
        verify(bookingRepository).save(booking);
    }

    @Test
    void deveRecusarForaDaJanelaDeSeteDias() {
        Booking booking = bookingConfirmada(Instant.now().minus(10, ChronoUnit.DAYS), LocalDate.now().plusDays(30));
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        CancelBookingByBuyerUseCase useCase = new CancelBookingByBuyerUseCase(bookingRepository, seatCountRepository);

        assertThatThrownBy(() -> useCase.cancel("booking-1")).isInstanceOf(CancellationWindowExpiredException.class);
        verify(bookingRepository, never()).save(booking);
    }

    @Test
    void deveRecusarComMenosDeQuarentaEOitoHorasDoPasseio() {
        Booking booking = bookingConfirmada(Instant.now(), LocalDate.now().plusDays(1));
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        CancelBookingByBuyerUseCase useCase = new CancelBookingByBuyerUseCase(bookingRepository, seatCountRepository);

        assertThatThrownBy(() -> useCase.cancel("booking-1")).isInstanceOf(CancellationWindowExpiredException.class);
    }

    @Test
    void deveRecusarStatusNaoCancelavel() {
        Booking booking = bookingConfirmada(Instant.now(), LocalDate.now().plusDays(30));
        booking.setStatus(BookingStatus.CANCELADA);
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        CancelBookingByBuyerUseCase useCase = new CancelBookingByBuyerUseCase(bookingRepository, seatCountRepository);

        assertThatThrownBy(() -> useCase.cancel("booking-1")).isInstanceOf(CancellationWindowExpiredException.class);
    }

    @Test
    void deveLancarNotFoundParaReservaInexistente() {
        when(bookingRepository.findById("inexistente")).thenReturn(Optional.empty());

        CancelBookingByBuyerUseCase useCase = new CancelBookingByBuyerUseCase(bookingRepository, seatCountRepository);

        assertThatThrownBy(() -> useCase.cancel("inexistente")).isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void devePermitirCancelamentoComOfertaDeTransferenciaPendente() {
        Booking booking = bookingConfirmada(Instant.now(), LocalDate.now().plusDays(30));
        booking.setStatus(BookingStatus.AGUARDANDO_TRANSFERENCIA);
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        CancelBookingByBuyerUseCase useCase = new CancelBookingByBuyerUseCase(bookingRepository, seatCountRepository);
        CancelBookingResult result = useCase.cancel("booking-1");

        assertThat(result.status()).isEqualTo(BookingStatus.REEMBOLSADA);
    }
}
