package com.empresa.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.domain.seathold.SeatHold;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatHoldRepository;

/**
 * T046 — sweeper de dupla responsabilidade: holds expirados (FR-004) e
 * ofertas de transferência sem resposta em 48h (FR-009).
 */
@ExtendWith(MockitoExtension.class)
class ReleaseExpiredHoldsJobTest {

    @Mock
    private SeatHoldRepository seatHoldRepository;

    @Mock
    private SeatCountRepository seatCountRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void deveLiberarSoHoldsExpiradosERemoveLos() {
        SeatHold expirado = SeatHold.builder()
                .id("hold-expirado").vesselId("vessel-1").data(LocalDate.of(2026, 12, 20)).tipoPasseio(TourType.ALTO_MAR)
                .quantidade(3).expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES)).build();
        SeatHold vigente = SeatHold.builder()
                .id("hold-vigente").vesselId("vessel-1").data(LocalDate.of(2026, 12, 20)).tipoPasseio(TourType.ALTO_MAR)
                .quantidade(1).expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES)).build();
        when(seatHoldRepository.findAll()).thenReturn(List.of(expirado, vigente));
        when(bookingRepository.findAwaitingTransferExpiredBefore(ArgumentMatchers.any())).thenReturn(List.of());

        new ReleaseExpiredHoldsJob(seatHoldRepository, seatCountRepository, bookingRepository, eventPublisher).run();

        verify(seatCountRepository).decrementHeld("vessel-1", LocalDate.of(2026, 12, 20), TourType.ALTO_MAR, 3);
        verify(seatHoldRepository).delete("hold-expirado");
        verify(seatHoldRepository, never()).delete("hold-vigente");
        verify(seatCountRepository, never()).decrementHeld(
                ArgumentMatchers.eq("vessel-1"), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.eq(1));
    }

    @Test
    void deveCancelarComReembolsoIntegralOfertaDeTransferenciaExpirada() {
        when(seatHoldRepository.findAll()).thenReturn(List.of());
        Booking expirada = Booking.builder()
                .id("booking-expirado").vesselId("vessel-1").data(LocalDate.of(2026, 12, 20)).tipoPasseio(TourType.ALTO_MAR)
                .quantidade(2).status(BookingStatus.AGUARDANDO_TRANSFERENCIA).targetVesselId("vessel-2")
                .transferAttemptId("transfer-1").transferOfferExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS)).build();
        when(bookingRepository.findAwaitingTransferExpiredBefore(ArgumentMatchers.any())).thenReturn(List.of(expirada));

        new ReleaseExpiredHoldsJob(seatHoldRepository, seatCountRepository, bookingRepository, eventPublisher).run();

        assertThat(expirada.getStatus()).isEqualTo(BookingStatus.REEMBOLSADA);
        assertThat(expirada.getTargetVesselId()).isNull();
        assertThat(expirada.getTransferAttemptId()).isNull();
        assertThat(expirada.getTransferOfferExpiresAt()).isNull();
        verify(seatCountRepository).decrementSold("vessel-1", LocalDate.of(2026, 12, 20), TourType.ALTO_MAR, 2);
        verify(bookingRepository).save(expirada);
    }

    @Test
    void naoDeveFalharSeOfertaExpiradaNaoTiverDataOuTipoPasseioReplicados() {
        when(seatHoldRepository.findAll()).thenReturn(List.of());
        Booking semDadosCompletos = Booking.builder()
                .id("booking-expirado").vesselId("vessel-1")
                .status(BookingStatus.AGUARDANDO_TRANSFERENCIA).targetVesselId("vessel-2")
                .transferAttemptId("transfer-1").transferOfferExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS)).build();
        when(bookingRepository.findAwaitingTransferExpiredBefore(ArgumentMatchers.any())).thenReturn(List.of(semDadosCompletos));

        new ReleaseExpiredHoldsJob(seatHoldRepository, seatCountRepository, bookingRepository, eventPublisher).run();

        assertThat(semDadosCompletos.getStatus()).isEqualTo(BookingStatus.REEMBOLSADA);
        verify(seatCountRepository, never()).decrementSold(
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt());
        verify(bookingRepository).save(semDadosCompletos);
    }
}
