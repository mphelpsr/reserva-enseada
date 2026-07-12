package com.empresa.booking.application.exception;

public class NoPendingTransferOfferException extends RuntimeException {

    public NoPendingTransferOfferException(String bookingId) {
        super("Reserva sem oferta de transferência pendente: " + bookingId);
    }
}
