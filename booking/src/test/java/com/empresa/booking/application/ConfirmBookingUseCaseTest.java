package com.empresa.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.empresa.booking.application.exception.HoldNotFoundException;
import com.empresa.booking.application.exception.PaymentRecebedorNotConfiguredException;
import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.domain.booking.VesselRecebedor;
import com.empresa.booking.domain.seathold.SeatHold;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatHoldRepository;
import com.empresa.booking.infrastructure.dynamodb.VesselRecebedorRepository;
import com.empresa.booking.infrastructure.payment.PagarmeClient;
import com.empresa.booking.infrastructure.payment.PagarmeOrderResult;
import com.empresa.booking.infrastructure.payment.PaymentFailedException;

/** T039 — FR-005 (confirmação só após pagamento aprovado), FR-015 (split de comissão configurável). */
@ExtendWith(MockitoExtension.class)
class ConfirmBookingUseCaseTest {

    @Mock
    private SeatHoldRepository seatHoldRepository;

    @Mock
    private SeatCountRepository seatCountRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private VesselRecebedorRepository vesselRecebedorRepository;

    @Mock
    private PagarmeClient pagarmeClient;

    private final SeatHold hold = SeatHold.builder()
            .id("hold-1").buyerId("buyer-1").vesselId("vessel-1")
            .data(LocalDate.now().plusDays(10)).tipoPasseio(TourType.ALTO_MAR).quantidade(2)
            .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES)).valorTotalCentavos(30000L).build();

    private final VesselRecebedor recebedor = VesselRecebedor.builder().vesselId("vessel-1").recebedorId("rec-1").build();

    @Test
    void deveConfirmarComSplitDeDozePorCentoPorPadrao() {
        when(seatHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));
        when(vesselRecebedorRepository.findByVesselId("vessel-1")).thenReturn(Optional.of(recebedor));
        when(pagarmeClient.createOrderWithSplit(anyString(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(new PagarmeOrderResult("order-1", "paid"));

        ConfirmBookingUseCase useCase = new ConfirmBookingUseCase(
                seatHoldRepository, seatCountRepository, bookingRepository, vesselRecebedorRepository, pagarmeClient, 12);
        Booking booking = useCase.confirm(new ConfirmBookingCommand("hold-1", "payment-ref-1"));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMADA);
        assertThat(booking.getValorPagoCentavos()).isEqualTo(30000L);
        assertThat(booking.getValorComissaoCentavos()).isEqualTo(3600L); // 12% de 30000
        assertThat(booking.getValorLiquidoCentavos()).isEqualTo(26400L);
        verify(seatCountRepository).moveHeldToSold("vessel-1", hold.getData(), TourType.ALTO_MAR, 2);
        verify(seatHoldRepository).delete("hold-1");
        verify(bookingRepository).save(booking);
    }

    @Test
    void deveRecusarHoldExpirado() {
        SeatHold holdExpirado = SeatHold.builder()
                .id("hold-1").buyerId("buyer-1").vesselId("vessel-1")
                .data(LocalDate.now().plusDays(10)).tipoPasseio(TourType.ALTO_MAR).quantidade(2)
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES)).valorTotalCentavos(30000L).build();
        when(seatHoldRepository.findById("hold-1")).thenReturn(Optional.of(holdExpirado));

        ConfirmBookingUseCase useCase = new ConfirmBookingUseCase(
                seatHoldRepository, seatCountRepository, bookingRepository, vesselRecebedorRepository, pagarmeClient, 12);

        assertThatThrownBy(() -> useCase.confirm(new ConfirmBookingCommand("hold-1", "payment-ref-1")))
                .isInstanceOf(HoldNotFoundException.class);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void deveRecusarSemRecebedorConfigurado() {
        when(seatHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));
        when(vesselRecebedorRepository.findByVesselId("vessel-1")).thenReturn(Optional.empty());

        ConfirmBookingUseCase useCase = new ConfirmBookingUseCase(
                seatHoldRepository, seatCountRepository, bookingRepository, vesselRecebedorRepository, pagarmeClient, 12);

        assertThatThrownBy(() -> useCase.confirm(new ConfirmBookingCommand("hold-1", "payment-ref-1")))
                .isInstanceOf(PaymentRecebedorNotConfiguredException.class);
        verify(pagarmeClient, never()).createOrderWithSplit(any(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void deveLancarQuandoPagamentoNaoForAprovado() {
        when(seatHoldRepository.findById("hold-1")).thenReturn(Optional.of(hold));
        when(vesselRecebedorRepository.findByVesselId("vessel-1")).thenReturn(Optional.of(recebedor));
        when(pagarmeClient.createOrderWithSplit(anyString(), anyString(), anyLong(), anyLong(), anyLong()))
                .thenReturn(new PagarmeOrderResult("order-1", "refused"));

        ConfirmBookingUseCase useCase = new ConfirmBookingUseCase(
                seatHoldRepository, seatCountRepository, bookingRepository, vesselRecebedorRepository, pagarmeClient, 12);

        assertThatThrownBy(() -> useCase.confirm(new ConfirmBookingCommand("hold-1", "payment-ref-1")))
                .isInstanceOf(PaymentFailedException.class);
        verify(bookingRepository, never()).save(any());
        verify(seatCountRepository, never()).moveHeldToSold(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
