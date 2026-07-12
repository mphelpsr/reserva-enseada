package com.empresa.booking.application;

import org.springframework.stereotype.Service;

import com.empresa.booking.application.exception.BookingNotFoundException;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.infrastructure.dynamodb.BookingRepository;

/** Detalhe de uma reserva (`GET /bookings/{id}`). */
@Service
public class GetBookingUseCase {

    private final BookingRepository bookingRepository;

    public GetBookingUseCase(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public Booking get(String bookingId) {
        return bookingRepository.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
    }
}
