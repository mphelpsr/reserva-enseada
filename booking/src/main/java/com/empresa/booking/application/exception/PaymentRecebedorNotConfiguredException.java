package com.empresa.booking.application.exception;

/**
 * FR-015: sem a réplica local da chave Pix do proprietário (`VesselRecebedor`,
 * via `vessel.recebedor.changed` — ver "Contrato da Saga" em plan.md), a
 * confirmação não pode montar o repasse e é recusada. Nunca chama nenhum
 * provedor de pagamento sem uma chave Pix válida.
 */
public class PaymentRecebedorNotConfiguredException extends RuntimeException {

    public PaymentRecebedorNotConfiguredException(String vesselId) {
        super("payment_recebedor_id não configurado para a embarcação: " + vesselId);
    }
}
