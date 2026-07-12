package com.empresa.booking.application.exception;

/**
 * FR-015: sem a réplica local de `payment_recebedor_id` (`VesselRecebedor`,
 * via `vessel.recebedor.changed` — ver "Contrato da Saga" em plan.md), a
 * confirmação não pode montar o split e é recusada. Nunca chama o Pagar.me
 * sem um recebedor válido.
 */
public class PaymentRecebedorNotConfiguredException extends RuntimeException {

    public PaymentRecebedorNotConfiguredException(String vesselId) {
        super("payment_recebedor_id não configurado para a embarcação: " + vesselId);
    }
}
