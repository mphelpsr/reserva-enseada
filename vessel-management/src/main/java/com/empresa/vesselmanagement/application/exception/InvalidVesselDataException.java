package com.empresa.vesselmanagement.application.exception;

/** FR-001: nome, capacidade máxima e porto/local de saída são obrigatórios. */
public class InvalidVesselDataException extends RuntimeException {

    public InvalidVesselDataException(String message) {
        super(message);
    }
}
