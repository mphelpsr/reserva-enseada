package com.empresa.vesselmanagement.infrastructure.dynamodb;

/** FR-009: já existe uma embarcação com o mesmo nº registro + CPF/CNPJ + nome legal. */
public class DuplicateVesselIdentifierException extends RuntimeException {

    public DuplicateVesselIdentifierException(String message) {
        super(message);
    }
}
