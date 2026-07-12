package com.empresa.booking.application.exception;

/** FR-014: passeio a menos de 24h da saída não pode mais ser comprado. */
public class MinimumAdvancePurchaseException extends RuntimeException {

    public MinimumAdvancePurchaseException(String vesselId, String data) {
        super("Antecedência mínima de 24h não respeitada para " + vesselId + "/" + data);
    }
}
