package com.empresa.vesselmanagement.application.exception;

/** FR-002: remoção direta não é permitida quando há reservas futuras confirmadas — exige transferência prévia. */
public class VesselHasFutureBookingsException extends RuntimeException {

    public VesselHasFutureBookingsException(String vesselId) {
        super("Embarcação " + vesselId + " tem reservas futuras confirmadas — transfira antes de remover (FR-002)");
    }
}
