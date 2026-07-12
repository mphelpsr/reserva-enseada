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

import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.domain.operatorevents.VesselTransferViable;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;

/** T043 — FR-009: notifica o comprador de uma oferta de transferência viável e abre a janela de 48h. */
@ExtendWith(MockitoExtension.class)
class ProcessTransferOfferUseCaseTest {

    @Mock
    private BookingRepository bookingRepository;

    private Booking booking(String id, BookingStatus status) {
        return Booking.builder()
                .id(id).buyerId("buyer-1").vesselId("vessel-1")
                .data(LocalDate.of(2026, 12, 20)).tipoPasseio(TourType.ALTO_MAR).quantidade(2)
                .status(status).build();
    }

    @Test
    void deveAbrirJanelaDeQuarentaEOitoHorasSoParaReservasConfirmadas() {
        Booking confirmada = booking("booking-1", BookingStatus.CONFIRMADA);
        Booking jaAguardando = booking("booking-2", BookingStatus.AGUARDANDO_TRANSFERENCIA);
        Booking cancelada = booking("booking-3", BookingStatus.CANCELADA);
        when(bookingRepository.findByVesselDateAndType("vessel-1", LocalDate.of(2026, 12, 20), TourType.ALTO_MAR))
                .thenReturn(List.of(confirmada, jaAguardando, cancelada));

        ProcessTransferOfferUseCase useCase = new ProcessTransferOfferUseCase(bookingRepository);
        useCase.process(new VesselTransferViable("transfer-1", "vessel-1", "2026-12-20", "alto_mar", "vessel-2", "avaria no motor"));

        assertThat(confirmada.getStatus()).isEqualTo(BookingStatus.AGUARDANDO_TRANSFERENCIA);
        assertThat(confirmada.getTargetVesselId()).isEqualTo("vessel-2");
        assertThat(confirmada.getTransferAttemptId()).isEqualTo("transfer-1");
        assertThat(confirmada.getMotivo()).isEqualTo("avaria no motor");
        assertThat(confirmada.getTransferOfferExpiresAt()).isNotNull();
        verify(bookingRepository).save(confirmada);
        verify(bookingRepository, never()).save(jaAguardando);
        verify(bookingRepository, never()).save(cancelada);
    }

    @Test
    void reservaJaCanceladaPeloCompradorDescartaAOfertaNaturalmente() {
        Booking cancelada = booking("booking-1", BookingStatus.REEMBOLSADA);
        when(bookingRepository.findByVesselDateAndType("vessel-1", LocalDate.of(2026, 12, 20), TourType.ALTO_MAR))
                .thenReturn(List.of(cancelada));

        ProcessTransferOfferUseCase useCase = new ProcessTransferOfferUseCase(bookingRepository);
        useCase.process(new VesselTransferViable("transfer-1", "vessel-1", "2026-12-20", "alto_mar", "vessel-2", "avaria no motor"));

        assertThat(cancelada.getStatus()).isEqualTo(BookingStatus.REEMBOLSADA);
        verify(bookingRepository, never()).save(cancelada);
    }
}
