package com.empresa.booking.api;

public record CreateHoldRequest(String buyerId, String vesselId, String data, String tipoPasseio, int quantidade) {
}
