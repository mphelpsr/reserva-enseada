package com.empresa.vesselmanagement.domain.availability;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** FR-012: os dois tipos de passeio, com disponibilidade configurada de forma independente por dia. */
public enum TourType {

    ALTO_MAR("alto_mar"),
    ORLA("orla");

    private final String value;

    TourType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TourType fromValue(String value) {
        for (TourType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Tipo de passeio inválido: " + value);
    }
}
