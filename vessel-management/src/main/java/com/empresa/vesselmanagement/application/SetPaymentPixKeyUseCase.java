package com.empresa.vesselmanagement.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.empresa.vesselmanagement.application.event.VesselRecebedorChangedEvent;
import com.empresa.vesselmanagement.application.exception.InvalidPaymentPixKeyException;
import com.empresa.vesselmanagement.domain.vessel.Owner;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.infrastructure.dynamodb.OwnerRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/**
 * T059c. FR-016 (revisão de 2026-07-12): o proprietário informa sua própria chave
 * Pix — não uma subconta de gateway — como `payment_recebedor_id`. Sem chamada a
 * nenhum gateway/provedor de pagamento aqui: só cadastro e propagação via evento
 * (`vessel.recebedor.changed`, `{vesselId, pixKey}`) para cada embarcação vinculada
 * ao proprietário. A integração real com o provedor de split Pix (Transfeera/OpenPix)
 * acontece do lado consumidor, no módulo booking.
 */
@Service
public class SetPaymentPixKeyUseCase {

    private final OwnerRepository ownerRepository;
    private final VesselRepository vesselRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SetPaymentPixKeyUseCase(
            OwnerRepository ownerRepository, VesselRepository vesselRepository, ApplicationEventPublisher eventPublisher) {
        this.ownerRepository = ownerRepository;
        this.vesselRepository = vesselRepository;
        this.eventPublisher = eventPublisher;
    }

    public Owner setPaymentPixKey(String ownerId, String pixKey) {
        if (pixKey == null || pixKey.isBlank()) {
            throw new InvalidPaymentPixKeyException("Chave Pix não pode ser vazia");
        }

        Owner owner = ownerRepository.findById(ownerId).orElseGet(() -> Owner.builder().id(ownerId).build());
        owner.setPaymentRecebedorId(pixKey);
        ownerRepository.save(owner);

        for (Vessel vessel : vesselRepository.findByOwnerId(ownerId)) {
            eventPublisher.publishEvent(new VesselRecebedorChangedEvent(vessel.getId(), pixKey));
        }

        return owner;
    }
}
