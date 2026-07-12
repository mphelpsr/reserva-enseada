package com.empresa.vesselmanagement.application.exception;

/** FR-016: uma embarcação não pode ficar `ativa` sem `payment_recebedor_id` válido do proprietário. */
public class PaymentRecebedorIdMissingException extends RuntimeException {

    public PaymentRecebedorIdMissingException(String ownerId) {
        super("Proprietário " + ownerId + " não tem payment_recebedor_id válido cadastrado (FR-016)");
    }
}
