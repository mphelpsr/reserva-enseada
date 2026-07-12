package com.empresa.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;

/** T044 — FR-011: histórico do comprador, via GSI1. */
@ExtendWith(MockitoExtension.class)
class ListBuyerBookingsUseCaseTest {

    @Mock
    private BookingRepository bookingRepository;

    @Test
    void deveDelegarParaORepositorioPorBuyerId() {
        Booking booking = Booking.builder()
                .id("booking-1").buyerId("buyer-1").vesselId("vessel-1")
                .data(LocalDate.of(2026, 12, 20)).tipoPasseio(TourType.ALTO_MAR).quantidade(1)
                .status(BookingStatus.CONFIRMADA).build();
        when(bookingRepository.findByBuyerId("buyer-1")).thenReturn(List.of(booking));

        ListBuyerBookingsUseCase useCase = new ListBuyerBookingsUseCase(bookingRepository);
        List<Booking> resultado = useCase.list("buyer-1");

        assertThat(resultado).containsExactly(booking);
    }
}
