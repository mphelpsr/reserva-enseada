package com.empresa.booking.application.exception;

/** FR-003: quantidade pedida excede as vagas restantes no momento da escrita condicional. */
public class InsufficientSeatsException extends RuntimeException {

    public InsufficientSeatsException(String vesselId, String data, String tipoPasseio) {
        super("Vagas insuficientes para " + vesselId + "/" + data + "/" + tipoPasseio);
    }
}
