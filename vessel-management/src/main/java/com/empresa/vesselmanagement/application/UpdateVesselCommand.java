package com.empresa.vesselmanagement.application;

import com.empresa.vesselmanagement.domain.vessel.VesselStatus;

/** FR-002: edição parcial — campos nulos são ignorados. */
public record UpdateVesselCommand(String nomeFantasia, String portoSaida, VesselStatus status) {
}
