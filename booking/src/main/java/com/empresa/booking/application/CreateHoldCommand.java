package com.empresa.booking.application;

import java.time.LocalDate;

import com.empresa.booking.domain.availability.TourType;

public record CreateHoldCommand(String buyerId, String vesselId, LocalDate data, TourType tipoPasseio, int quantidade) {
}
