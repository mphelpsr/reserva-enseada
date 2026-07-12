package com.empresa.vesselmanagement.api;

import java.time.LocalDate;

import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.seatlimit.SeatLimitOrigin;

public record SetSeatLimitResponse(
        String vesselId,
        LocalDate data,
        TourType tipoPasseio,
        int limite,
        SeatLimitOrigin origem,
        int vezesPadraoAplicado) {
}
