package com.empresa.vesselmanagement.domain.cancellation;

/** Estado de uma tentativa de transferência de reserva(s) dentro da mesma frota (Princípio VII). */
public enum TransferAttemptStatus {
    /** Embarcação alternativa encontrada; aguarda confirmação do comprador (até 48h, módulo booking). */
    VIABLE_PENDING,
    /** Comprador aceitou; booking publicou `booking.transferred` (fechado por T059). */
    TRANSFERRED,
    /** Comprador recusou/expirou, ou nenhuma embarcação alternativa foi encontrada — reembolso integral. */
    CANCELLED_NO_ALTERNATIVE
}
