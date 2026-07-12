package com.empresa.booking.domain.booking;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Key Entities do spec.md lista (retida, confirmada, cancelada, transferida,
 * reembolsada) como os status principais; AGUARDANDO_TRANSFERENCIA é um
 * estado intermediário necessário para FR-009 (janela de 48h de resposta do
 * comprador a uma oferta de transferência) — sem ele não haveria como
 * distinguir "reserva confirmada normal" de "reserva com transferência
 * pendente de confirmação".
 */
public enum BookingStatus {

    RETIDA("retida"),
    CONFIRMADA("confirmada"),
    AGUARDANDO_TRANSFERENCIA("aguardando_transferencia"),
    CANCELADA("cancelada"),
    TRANSFERIDA("transferida"),
    REEMBOLSADA("reembolsada");

    private final String value;

    BookingStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static BookingStatus fromValue(String value) {
        for (BookingStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status de reserva inválido: " + value);
    }
}
