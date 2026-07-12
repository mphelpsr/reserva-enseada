package com.empresa.vesselmanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.empresa.vesselmanagement.application.exception.PaymentRecebedorIdMissingException;
import com.empresa.vesselmanagement.domain.vessel.Owner;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.OwnerRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/** T039 — FR-002, FR-016 (cenário 7 do spec.md). */
@ExtendWith(MockitoExtension.class)
class UpdateVesselUseCaseTest {

    @Mock
    private VesselRepository vesselRepository;

    @Mock
    private OwnerRepository ownerRepository;

    private Vessel vessel(VesselStatus status) {
        return Vessel.builder()
                .id("vessel-1").ownerId("owner-1").nomeLegal("Nome").capacidadeMaxima(20)
                .portoSaida("Porto A").status(status).build();
    }

    @Test
    void naoDevePermitirAtivarSemPaymentRecebedorIdValido() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel(VesselStatus.PENDENTE_CONFIGURACAO)));
        when(ownerRepository.findById("owner-1"))
                .thenReturn(Optional.of(Owner.builder().id("owner-1").paymentRecebedorId(null).build()));

        UpdateVesselUseCase useCase = new UpdateVesselUseCase(vesselRepository, ownerRepository);
        var command = new UpdateVesselCommand(null, null, VesselStatus.ATIVA);

        assertThatThrownBy(() -> useCase.update("vessel-1", command))
                .isInstanceOf(PaymentRecebedorIdMissingException.class);
    }

    @Test
    void devePermitirAtivarComPaymentRecebedorIdValido() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel(VesselStatus.PENDENTE_CONFIGURACAO)));
        when(ownerRepository.findById("owner-1"))
                .thenReturn(Optional.of(Owner.builder().id("owner-1").paymentRecebedorId("recebedor-123").build()));

        UpdateVesselUseCase useCase = new UpdateVesselUseCase(vesselRepository, ownerRepository);
        var command = new UpdateVesselCommand(null, null, VesselStatus.ATIVA);

        Vessel updated = useCase.update("vessel-1", command);

        assertThat(updated.getStatus()).isEqualTo(VesselStatus.ATIVA);
    }

    @Test
    void editarCamposCadastraisNaoExigePaymentRecebedorId() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel(VesselStatus.PENDENTE_CONFIGURACAO)));

        UpdateVesselUseCase useCase = new UpdateVesselUseCase(vesselRepository, ownerRepository);
        var command = new UpdateVesselCommand("Novo Nome Fantasia", "Novo Porto", null);

        Vessel updated = useCase.update("vessel-1", command);

        assertThat(updated.getNomeFantasia()).isEqualTo("Novo Nome Fantasia");
        assertThat(updated.getPortoSaida()).isEqualTo("Novo Porto");
    }
}
