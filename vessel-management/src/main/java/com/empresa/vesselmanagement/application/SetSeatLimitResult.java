package com.empresa.vesselmanagement.application;

import com.empresa.vesselmanagement.domain.seatlimit.PlatformSeatLimit;

/** FR-015. `vezesPadraoAplicado` é o valor do contador cumulativo após esta operação. */
public record SetSeatLimitResult(PlatformSeatLimit seatLimit, int vezesPadraoAplicado) {
}
