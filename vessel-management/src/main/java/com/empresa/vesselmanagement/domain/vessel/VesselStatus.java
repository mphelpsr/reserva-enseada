package com.empresa.vesselmanagement.domain.vessel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** FR-001, FR-016. Uma embarcação só pode ficar ATIVA com payment_recebedor_id válido do proprietário. */
public enum VesselStatus {

    PENDENTE_CONFIGURACAO("pendente_configuracao"),
    ATIVA("ativa"),
    INATIVA("inativa");

    private final String value;

    VesselStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static VesselStatus fromValue(String value) {
        for (VesselStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Status de embarcação inválido: " + value);
    }
}
