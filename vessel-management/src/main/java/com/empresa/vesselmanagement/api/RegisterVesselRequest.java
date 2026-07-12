package com.empresa.vesselmanagement.api;

public record RegisterVesselRequest(
        String ownerId,
        String nomeLegal,
        String nomeFantasia,
        String numeroRegistroCapitania,
        String cpfCnpjProprietario,
        Integer capacidadeMaxima,
        String portoSaida) {
}
