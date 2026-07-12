package com.empresa.booking.application.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String bookingId) {
        super("Reserva não encontrada: " + bookingId);
    }
}
