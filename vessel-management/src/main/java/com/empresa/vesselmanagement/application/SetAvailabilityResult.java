package com.empresa.vesselmanagement.application;

import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;

/** FR-003, FR-004, FR-007. Três desfechos possíveis de SetAvailabilityUseCase. */
public sealed interface SetAvailabilityResult {

    /** Caminho normal (FR-003/004): sem reserva confirmada, efeito imediato. */
    record Applied(DeclaredAvailability availability) implements SetAvailabilityResult {
    }

    /** FR-007 + Princípio VII: há reserva confirmada e uma embarcação alternativa da
     *  mesma frota tem vaga — disponibilidade original NÃO muda ainda (pendente). */
    record TransferPending(String alternativeVesselId) implements SetAvailabilityResult {
    }

    /** FR-007: há reserva confirmada e NENHUMA alternativa — cancelamento com
     *  reembolso integral é disparado de imediato, efeito final na disponibilidade. */
    record CancellationInitiated(DeclaredAvailability availability) implements SetAvailabilityResult {
    }
}
