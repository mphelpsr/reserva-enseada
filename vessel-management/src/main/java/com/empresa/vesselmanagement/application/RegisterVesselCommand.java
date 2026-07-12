package com.empresa.vesselmanagement.application;

/** FR-001, FR-009. */
public record RegisterVesselCommand(
        String ownerId,
        String nomeLegal,
        String nomeFantasia,
        String numeroRegistroCapitania,
        String cpfCnpjProprietario,
        Integer capacidadeMaxima,
        String portoSaida) {
}
