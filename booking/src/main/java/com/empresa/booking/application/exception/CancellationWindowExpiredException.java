package com.empresa.booking.application.exception;

/** FR-006/FR-007: fora da janela de arrependimento — modelo binário, sem escalonamento. */
public class CancellationWindowExpiredException extends RuntimeException {

    public CancellationWindowExpiredException(String bookingId) {
        super("Janela de cancelamento expirada para a reserva: " + bookingId);
    }
}
