package com.empresa.vesselmanagement.domain.seatlimit;

/**
 * FR-015: origem do limite de vagas de um dia/tipo de passeio.
 * MANUAL = proprietário indicou explicitamente.
 * PADRAO_AUTOMATICO = 10% da capacidade máxima aplicado por ausência de indicação (1ª/2ª vez).
 * ZERO_SEM_PADRAO = ausência de indicação a partir da 3ª vez — zero vagas até definição explícita.
 */
public enum SeatLimitOrigin {
    MANUAL,
    PADRAO_AUTOMATICO,
    ZERO_SEM_PADRAO
}
