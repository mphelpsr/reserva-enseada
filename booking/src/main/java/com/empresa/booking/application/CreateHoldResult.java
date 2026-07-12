package com.empresa.booking.application;

import java.time.Instant;

public record CreateHoldResult(String holdId, Instant expiresAt, String vesselId, int quantidade) {
}
