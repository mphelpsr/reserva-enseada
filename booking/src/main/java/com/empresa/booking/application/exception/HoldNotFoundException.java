package com.empresa.booking.application.exception;

public class HoldNotFoundException extends RuntimeException {

    public HoldNotFoundException(String holdId) {
        super("Hold não encontrado ou expirado: " + holdId);
    }
}
