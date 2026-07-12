package com.empresa.vesselmanagement.application;

/** FR-002. */
public record TransferVesselResult(String targetVesselId, int transferredBookingsCount) {
}
