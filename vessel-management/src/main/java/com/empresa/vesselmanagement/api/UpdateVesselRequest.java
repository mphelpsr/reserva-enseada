package com.empresa.vesselmanagement.api;

import com.empresa.vesselmanagement.domain.vessel.VesselStatus;

public record UpdateVesselRequest(String nomeFantasia, String portoSaida, VesselStatus status) {
}
