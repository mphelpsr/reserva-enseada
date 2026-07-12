package com.empresa.vesselmanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.empresa.vesselmanagement.application.exception.InvalidVesselDataException;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/** T038 — FR-001, FR-009. */
@ExtendWith(MockitoExtension.class)
class RegisterVesselUseCaseTest {

    @Mock
    private VesselRepository vesselRepository;

    @Test
    void deveCadastrarComStatusPendenteConfiguracao() {
        RegisterVesselUseCase useCase = new RegisterVesselUseCase(vesselRepository);
        var command = new RegisterVesselCommand("owner-1", "Sereia do Mar", "Passeios Sereia", "CP-1", "111", 20, "Porto A");

        Vessel vessel = useCase.register(command);

        assertThat(vessel.getStatus()).isEqualTo(VesselStatus.PENDENTE_CONFIGURACAO);
        assertThat(vessel.getId()).isNotBlank();
        assertThat(vessel.getOwnerId()).isEqualTo("owner-1");
        verify(vesselRepository).create(any(Vessel.class));
    }

    @Test
    void deveRecusarSemNomeLegal() {
        RegisterVesselUseCase useCase = new RegisterVesselUseCase(vesselRepository);
        var command = new RegisterVesselCommand("owner-1", null, "Fantasia", "CP-1", "111", 20, "Porto A");

        assertThatThrownBy(() -> useCase.register(command)).isInstanceOf(InvalidVesselDataException.class);
    }

    @Test
    void deveRecusarSemCapacidadeMaximaPositiva() {
        RegisterVesselUseCase useCase = new RegisterVesselUseCase(vesselRepository);
        var command = new RegisterVesselCommand("owner-1", "Nome", "Fantasia", "CP-1", "111", 0, "Porto A");

        assertThatThrownBy(() -> useCase.register(command)).isInstanceOf(InvalidVesselDataException.class);
    }

    @Test
    void deveRecusarSemPortoSaida() {
        RegisterVesselUseCase useCase = new RegisterVesselUseCase(vesselRepository);
        var command = new RegisterVesselCommand("owner-1", "Nome", "Fantasia", "CP-1", "111", 20, null);

        assertThatThrownBy(() -> useCase.register(command)).isInstanceOf(InvalidVesselDataException.class);
    }
}
