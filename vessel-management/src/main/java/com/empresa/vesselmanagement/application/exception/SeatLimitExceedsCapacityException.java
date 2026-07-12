package com.empresa.vesselmanagement.application.exception;

/** FR-015: o limite de vagas na plataforma deve ser ≤ capacidade máxima da embarcação. */
public class SeatLimitExceedsCapacityException extends RuntimeException {

    public SeatLimitExceedsCapacityException(int limite, int capacidadeMaxima) {
        super("Limite " + limite + " excede a capacidade máxima da embarcação (" + capacidadeMaxima + ")");
    }
}
