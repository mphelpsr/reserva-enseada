package com.empresa.vesselmanagement.application.exception;

/** FR-006/FR-008: ainda não há advisory calculado para este dia (job assíncrono, T057). */
public class AdvisoryNotFoundException extends RuntimeException {

    public AdvisoryNotFoundException(String vesselId, String data) {
        super("Advisory não calculado para " + vesselId + "/" + data);
    }
}
