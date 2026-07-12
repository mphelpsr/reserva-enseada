package com.empresa.vesselmanagement.api;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.empresa.vesselmanagement.application.SetAvailabilityResult;
import com.empresa.vesselmanagement.application.SetAvailabilityUseCase;
import com.empresa.vesselmanagement.application.SetRotationScheduleUseCase;
import com.empresa.vesselmanagement.domain.availability.RotationSchedule;
import com.empresa.vesselmanagement.domain.availability.TourType;

/** T048. FR-003, FR-004, FR-007, FR-013, FR-014. */
@RestController
@RequestMapping("/vessels/{vesselId}")
public class AvailabilityController {

    private final SetAvailabilityUseCase setAvailabilityUseCase;
    private final SetRotationScheduleUseCase setRotationScheduleUseCase;

    public AvailabilityController(SetAvailabilityUseCase setAvailabilityUseCase, SetRotationScheduleUseCase setRotationScheduleUseCase) {
        this.setAvailabilityUseCase = setAvailabilityUseCase;
        this.setRotationScheduleUseCase = setRotationScheduleUseCase;
    }

    @PutMapping("/availability/{data}/{tipoPasseio}")
    public ResponseEntity<Object> setAvailability(
            @PathVariable String vesselId,
            @PathVariable LocalDate data,
            @PathVariable TourType tipoPasseio,
            @RequestBody SetAvailabilityRequest request) {
        SetAvailabilityResult result =
                setAvailabilityUseCase.setAvailability(vesselId, data, tipoPasseio, request.disponivel(), request.motivo());

        return switch (result) {
            case SetAvailabilityResult.Applied applied -> ResponseEntity.ok(applied.availability());
            case SetAvailabilityResult.TransferPending pending -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new TransferPendingResponse("TRANSFERENCIA_EM_ANDAMENTO", pending.alternativeVesselId()));
            case SetAvailabilityResult.CancellationInitiated cancelled -> ResponseEntity.ok(
                    new CancellationInitiatedResponse("CANCELAMENTO_INICIADO", cancelled.availability().isDisponivel()));
        };
    }

    @PutMapping("/rotation/{data}")
    public RotationSchedule setRotation(
            @PathVariable String vesselId, @PathVariable LocalDate data, @RequestBody SetRotationRequest request) {
        return setRotationScheduleUseCase.setRotation(vesselId, data, request.bloqueado());
    }
}
