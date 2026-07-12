package com.empresa.vesselmanagement.application;

import org.springframework.stereotype.Service;

import com.empresa.vesselmanagement.application.exception.PaymentRecebedorIdMissingException;
import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.domain.vessel.Owner;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.OwnerRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/**
 * T039. FR-002: edição de dados cadastrais. Ativar (status=ativa) exige
 * `payment_recebedor_id` válido do proprietário (FR-016, Acceptance Scenario 7)
 * — nunca bloqueia os demais campos, só a transição para `ativa`.
 */
@Service
public class UpdateVesselUseCase {

    private final VesselRepository vesselRepository;
    private final OwnerRepository ownerRepository;

    public UpdateVesselUseCase(VesselRepository vesselRepository, OwnerRepository ownerRepository) {
        this.vesselRepository = vesselRepository;
        this.ownerRepository = ownerRepository;
    }

    public Vessel update(String vesselId, UpdateVesselCommand command) {
        Vessel vessel = vesselRepository.findById(vesselId).orElseThrow(() -> new VesselNotFoundException(vesselId));

        if (command.nomeFantasia() != null) {
            vessel.setNomeFantasia(command.nomeFantasia());
        }
        if (command.portoSaida() != null) {
            vessel.setPortoSaida(command.portoSaida());
        }
        if (command.status() == VesselStatus.ATIVA && vessel.getStatus() != VesselStatus.ATIVA) {
            Owner owner = ownerRepository.findById(vessel.getOwnerId())
                    .orElseThrow(() -> new PaymentRecebedorIdMissingException(vessel.getOwnerId()));
            if (!owner.hasValidPaymentRecebedorId()) {
                throw new PaymentRecebedorIdMissingException(vessel.getOwnerId());
            }
            vessel.setStatus(VesselStatus.ATIVA);
        } else if (command.status() != null) {
            vessel.setStatus(command.status());
        }

        vesselRepository.update(vessel);
        return vessel;
    }
}
