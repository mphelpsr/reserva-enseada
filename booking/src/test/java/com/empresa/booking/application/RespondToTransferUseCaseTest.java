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
import com.empresa.booking.application.exception.NoPendingTransferOfferException;
import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;

/** T041 — FR-009: resposta do comprador a uma oferta de transferência pendente. */
@ExtendWith(MockitoExtension.class)
class RespondToTransferUseCaseTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatCountRepository seatCountRepository;

    private Booking bookingAguardandoTransferencia() {
        return Booking.builder()
                .id("booking-1").buyerId("buyer-1").vesselId("vessel-1")
                .data(LocalDate.now().plusDays(30)).tipoPasseio(TourType.ALTO_MAR).quantidade(2)
                .status(BookingStatus.AGUARDANDO_TRANSFERENCIA)
                .targetVesselId("vessel-2").transferAttemptId("transfer-1")
                .transferOfferExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
    }

    @Test
    void aceiteDeveMoverParaEmbarcacaoDestinoEAtualizarVagasNasDuasPontas() {
        Booking booking = bookingAguardandoTransferencia();
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        RespondToTransferUseCase useCase = new RespondToTransferUseCase(bookingRepository, seatCountRepository);
        Booking resultado = useCase.respond("booking-1", true);

        assertThat(resultado.getStatus()).isEqualTo(BookingStatus.TRANSFERIDA);
        assertThat(resultado.getVesselId()).isEqualTo("vessel-2");
        assertThat(resultado.getTargetVesselId()).isNull();
        assertThat(resultado.getTransferOfferExpiresAt()).isNull();
        verify(seatCountRepository).decrementSold("vessel-1", booking.getData(), TourType.ALTO_MAR, 2);
        verify(seatCountRepository).incrementSold("vessel-2", booking.getData(), TourType.ALTO_MAR, 2);
        verify(bookingRepository).moveToVessel(booking, "vessel-1");
        verify(bookingRepository, never()).save(booking);
    }

    @Test
    void recusaDeveReembolsarIntegralmenteSemChecarJanelaDeCancelamento() {
        Booking booking = bookingAguardandoTransferencia();
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        RespondToTransferUseCase useCase = new RespondToTransferUseCase(bookingRepository, seatCountRepository);
        Booking resultado = useCase.respond("booking-1", false);

        assertThat(resultado.getStatus()).isEqualTo(BookingStatus.REEMBOLSADA);
        assertThat(resultado.getVesselId()).isEqualTo("vessel-1");
        assertThat(resultado.getTargetVesselId()).isNull();
        verify(seatCountRepository).decrementSold("vessel-1", booking.getData(), TourType.ALTO_MAR, 2);
        verify(seatCountRepository, never()).incrementSold(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(bookingRepository).save(booking);
        verify(bookingRepository, never()).moveToVessel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deveRecusarQuandoNaoHaOfertaPendente() {
        Booking booking = bookingAguardandoTransferencia();
        booking.setStatus(BookingStatus.CONFIRMADA);
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));

        RespondToTransferUseCase useCase = new RespondToTransferUseCase(bookingRepository, seatCountRepository);

        assertThatThrownBy(() -> useCase.respond("booking-1", true)).isInstanceOf(NoPendingTransferOfferException.class);
    }

    @Test
    void deveLancarNotFoundParaReservaInexistente() {
        when(bookingRepository.findById("inexistente")).thenReturn(Optional.empty());

        RespondToTransferUseCase useCase = new RespondToTransferUseCase(bookingRepository, seatCountRepository);

        assertThatThrownBy(() -> useCase.respond("inexistente", true)).isInstanceOf(BookingNotFoundException.class);
    }
}
