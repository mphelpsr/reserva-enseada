package com.empresa.vesselmanagement.api;

public record VesselHasFutureBookingsResponse(String error, String message, boolean requiresTransferFirst) {
}
