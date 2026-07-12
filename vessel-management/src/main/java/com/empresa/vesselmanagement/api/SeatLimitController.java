package com.empresa.vesselmanagement.api;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.empresa.vesselmanagement.application.SetSeatLimitResult;
import com.empresa.vesselmanagement.application.SetSeatLimitUseCase;
import com.empresa.vesselmanagement.domain.availability.TourType;

/** T049. FR-015. */
@RestController
@RequestMapping("/vessels/{vesselId}/seat-limit")
public class SeatLimitController {

    private final SetSeatLimitUseCase setSeatLimitUseCase;

    public SeatLimitController(SetSeatLimitUseCase setSeatLimitUseCase) {
        this.setSeatLimitUseCase = setSeatLimitUseCase;
    }

    @PutMapping("/{data}/{tipoPasseio}")
    public SetSeatLimitResponse setSeatLimit(
            @PathVariable String vesselId,
            @PathVariable LocalDate data,
            @PathVariable TourType tipoPasseio,
            @RequestBody SetSeatLimitRequest request) {
        SetSeatLimitResult result = setSeatLimitUseCase.setSeatLimit(vesselId, data, tipoPasseio, request.limite());
        return new SetSeatLimitResponse(
                result.seatLimit().getVesselId(),
                result.seatLimit().getData(),
                result.seatLimit().getTipoPasseio(),
                result.seatLimit().getLimite(),
                result.seatLimit().getOrigem(),
                result.vezesPadraoAplicado());
    }
}
