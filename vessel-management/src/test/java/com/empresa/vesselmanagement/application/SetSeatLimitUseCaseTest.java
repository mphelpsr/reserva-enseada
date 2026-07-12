package com.empresa.vesselmanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.empresa.vesselmanagement.application.exception.SeatLimitExceedsCapacityException;
import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.seatlimit.DefaultSeatUsageCounter;
import com.empresa.vesselmanagement.domain.seatlimit.SeatLimitOrigin;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.SeatLimitRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/** T043 — FR-015, cenários 6/6a: padrão automático de 10% até 2x, depois zero vagas. */
@ExtendWith(MockitoExtension.class)
class SetSeatLimitUseCaseTest {

    @Mock
    private VesselRepository vesselRepository;

    @Mock
    private SeatLimitRepository seatLimitRepository;

    private final Vessel vessel = Vessel.builder()
            .id("vessel-1").ownerId("owner-1").nomeLegal("Nome").capacidadeMaxima(20)
            .portoSaida("Porto A").status(VesselStatus.ATIVA).build();

    @Test
    void primeiraAusenciaDeIndicacaoAplicaDezPorCento() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));
        when(seatLimitRepository.getCounter("vessel-1"))
                .thenReturn(DefaultSeatUsageCounter.builder().vesselId("vessel-1").vezesAplicado(0).build());
        when(seatLimitRepository.incrementCounter("vessel-1")).thenReturn(1);

        SetSeatLimitUseCase useCase = new SetSeatLimitUseCase(vesselRepository, seatLimitRepository);
        SetSeatLimitResult result = useCase.setSeatLimit("vessel-1", LocalDate.of(2026, 11, 1), TourType.ALTO_MAR, null);

        assertThat(result.seatLimit().getLimite()).isEqualTo(2); // 10% de 20
        assertThat(result.seatLimit().getOrigem()).isEqualTo(SeatLimitOrigin.PADRAO_AUTOMATICO);
        assertThat(result.vezesPadraoAplicado()).isEqualTo(1);
    }

    @Test
    void terceiraAusenciaDeIndicacaoZeraVagasSemIncrementarContador() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));
        when(seatLimitRepository.getCounter("vessel-1"))
                .thenReturn(DefaultSeatUsageCounter.builder().vesselId("vessel-1").vezesAplicado(2).build());

        SetSeatLimitUseCase useCase = new SetSeatLimitUseCase(vesselRepository, seatLimitRepository);
        SetSeatLimitResult result = useCase.setSeatLimit("vessel-1", LocalDate.of(2026, 11, 3), TourType.ALTO_MAR, null);

        assertThat(result.seatLimit().getLimite()).isZero();
        assertThat(result.seatLimit().getOrigem()).isEqualTo(SeatLimitOrigin.ZERO_SEM_PADRAO);
        assertThat(result.vezesPadraoAplicado()).isEqualTo(2);
    }

    @Test
    void indicacaoExplicitaNaoIncrementaContador() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));
        when(seatLimitRepository.getCounter("vessel-1"))
                .thenReturn(DefaultSeatUsageCounter.builder().vesselId("vessel-1").vezesAplicado(0).build());

        SetSeatLimitUseCase useCase = new SetSeatLimitUseCase(vesselRepository, seatLimitRepository);
        SetSeatLimitResult result = useCase.setSeatLimit("vessel-1", LocalDate.of(2026, 11, 1), TourType.ALTO_MAR, 7);

        assertThat(result.seatLimit().getLimite()).isEqualTo(7);
        assertThat(result.seatLimit().getOrigem()).isEqualTo(SeatLimitOrigin.MANUAL);
    }

    @Test
    void deveRecusarLimiteAcimaDaCapacidadeMaxima() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));

        SetSeatLimitUseCase useCase = new SetSeatLimitUseCase(vesselRepository, seatLimitRepository);

        assertThatThrownBy(() -> useCase.setSeatLimit("vessel-1", LocalDate.of(2026, 11, 1), TourType.ALTO_MAR, 999))
                .isInstanceOf(SeatLimitExceedsCapacityException.class);
    }

    @Test
    void deveLancarNotFoundParaEmbarcacaoInexistente() {
        when(vesselRepository.findById("inexistente")).thenReturn(Optional.empty());

        SetSeatLimitUseCase useCase = new SetSeatLimitUseCase(vesselRepository, seatLimitRepository);

        assertThatThrownBy(() -> useCase.setSeatLimit("inexistente", LocalDate.of(2026, 11, 1), TourType.ALTO_MAR, 5))
                .isInstanceOf(VesselNotFoundException.class);
    }
}
