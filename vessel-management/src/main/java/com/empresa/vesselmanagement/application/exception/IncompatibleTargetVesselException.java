package com.empresa.vesselmanagement.application.exception;

/** FR-002: embarcação de destino precisa da mesma capacidade mínima e porto de saída. */
public class IncompatibleTargetVesselException extends RuntimeException {

    public IncompatibleTargetVesselException(String targetVesselId) {
        super("Embarcação de destino incompatível (capacidade/porto de saída): " + targetVesselId);
    }
}
