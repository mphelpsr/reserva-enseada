package com.empresa.vesselmanagement.application.exception;

/** FR-016: a chave Pix informada pelo proprietário não pode ser nula/vazia. */
public class InvalidPaymentPixKeyException extends RuntimeException {

    public InvalidPaymentPixKeyException(String message) {
        super(message);
    }
}
