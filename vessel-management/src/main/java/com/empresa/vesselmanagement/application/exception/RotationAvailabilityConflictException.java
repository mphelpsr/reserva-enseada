package com.empresa.vesselmanagement.application.exception;

import java.time.LocalDate;

/**
 * FR-014: o dia de rodízio cadastrado SEMPRE prevalece sobre uma tentativa de
 * marcar Alto Mar como disponível no mesmo dia/embarcação — nos dois sentidos
 * (cadastrar rodízio quando Alto Mar já está disponível, ou marcar Alto Mar
 * disponível quando já existe rodízio). A coexistência silenciosa nunca é
 * permitida; o proprietário precisa escolher explicitamente uma das duas saídas
 * (mudar a data da disponibilidade, ou alterar/remover o rodízio) — a escolha em
 * si é responsabilidade do controller/frontend (Fase 3.4), este objeto só carrega
 * o contexto do conflito.
 */
public class RotationAvailabilityConflictException extends RuntimeException {

    private final String vesselId;
    private final LocalDate data;

    public RotationAvailabilityConflictException(String vesselId, LocalDate data) {
        super("Conflito rodízio x Alto Mar em " + vesselId + "/" + data + " (FR-014)");
        this.vesselId = vesselId;
        this.data = data;
    }

    public String getVesselId() {
        return vesselId;
    }

    public LocalDate getData() {
        return data;
    }
}
