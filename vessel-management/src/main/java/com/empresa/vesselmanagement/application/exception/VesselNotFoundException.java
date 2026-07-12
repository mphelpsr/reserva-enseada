package com.empresa.vesselmanagement.application.exception;

public class VesselNotFoundException extends RuntimeException {

    public VesselNotFoundException(String vesselId) {
        super("Embarcação não encontrada: " + vesselId);
    }
}
