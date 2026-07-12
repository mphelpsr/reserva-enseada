package com.empresa.booking.domain.availability;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Espelha vessel-management: os dois tipos de passeio, disponibilidade independente por dia. */
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
