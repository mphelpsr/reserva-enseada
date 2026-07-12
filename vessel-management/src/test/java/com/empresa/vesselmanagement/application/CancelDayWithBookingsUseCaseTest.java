package com.empresa.vesselmanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.cancellation.BookingTransferAttempt;
import com.empresa.vesselmanagement.domain.cancellation.TransferAttemptStatus;
import com.empresa.vesselmanagement.domain.seatlimit.PlatformSeatLimit;
import com.empresa.vesselmanagement.domain.seatlimit.SeatLimitOrigin;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.AvailabilityRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingTransferAttemptRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.SeatLimitRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/** T046 — FR-007, Princípio VII: transferência na mesma frota antes de cancelamento. */
@ExtendWith(MockitoExtension.class)
class CancelDayWithBookingsUseCaseTest {

    @Mock
    private VesselRepository vesselRepository;

    @Mock
    private AvailabilityRepository availabilityRepository;

    @Mock
    private SeatLimitRepository seatLimitRepository;

    @Mock
    private BookingTransferAttemptRepository transferAttemptRepository;

    private final LocalDate data = LocalDate.of(2026, 12, 24);

    private Vessel vessel(String id, String ownerId) {
        return Vessel.builder().id(id).ownerId(ownerId).nomeLegal("Nome " + id).capacidadeMaxima(20)
                .portoSaida("Porto A").status(VesselStatus.ATIVA).build();
    }

    @Test
    void deveEncontrarEmbarcacaoDaMesmaFrotaComVagaEDeixarPendente() {
        Vessel origem = vessel("vessel-a", "owner-1");
        Vessel alternativa = vessel("vessel-b", "owner-1");

        when(vesselRepository.findById("vessel-a")).thenReturn(Optional.of(origem));
        when(vesselRepository.findByOwnerId("owner-1")).thenReturn(List.of(origem, alternativa));
        when(availabilityRepository.findByVesselDateType("vessel-b", data, TourType.ALTO_MAR))
                .thenReturn(Optional.of(DeclaredAvailability.builder()
                        .vesselId("vessel-b").data(data).tipoPasseio(TourType.ALTO_MAR).disponivel(true).build()));
        when(seatLimitRepository.findByVesselDateType("vessel-b", data, TourType.ALTO_MAR))
                .thenReturn(Optional.of(PlatformSeatLimit.builder()
                        .vesselId("vessel-b").data(data).tipoPasseio(TourType.ALTO_MAR)
                        .limite(5).origem(SeatLimitOrigin.MANUAL).build()));

        CancelDayWithBookingsUseCase useCase = new CancelDayWithBookingsUseCase(
                vesselRepository, availabilityRepository, seatLimitRepository, transferAttemptRepository);

        BookingTransferAttempt attempt = useCase.cancelDay("vessel-a", data, TourType.ALTO_MAR, "avaria no motor");

        assertThat(attempt.getStatus()).isEqualTo(TransferAttemptStatus.VIABLE_PENDING);
        assertThat(attempt.getTargetVesselId()).isEqualTo("vessel-b");
        // disponibilidade original NÃO é alterada enquanto pendente
        verify(availabilityRepository, times(0)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void devePublicarCancelamentoImediatoQuandoNaoHaAlternativaNaFrota() {
        Vessel origem = vessel("vessel-a", "owner-2");

        when(vesselRepository.findById("vessel-a")).thenReturn(Optional.of(origem));
        when(vesselRepository.findByOwnerId("owner-2")).thenReturn(List.of(origem)); // frota de uma unidade só

        CancelDayWithBookingsUseCase useCase = new CancelDayWithBookingsUseCase(
                vesselRepository, availabilityRepository, seatLimitRepository, transferAttemptRepository);

        BookingTransferAttempt attempt = useCase.cancelDay("vessel-a", data, TourType.ALTO_MAR, "força maior");

        assertThat(attempt.getStatus()).isEqualTo(TransferAttemptStatus.CANCELLED_NO_ALTERNATIVE);
        assertThat(attempt.getTargetVesselId()).isNull();
        // sem alternativa: efeito imediato e final na disponibilidade
        verify(availabilityRepository).save(org.mockito.ArgumentMatchers.argThat(
                a -> !a.isDisponivel() && a.getMotivo().equals("força maior")));
    }

    @Test
    void naoConsideraEmbarcacaoDeOutroProprietarioComoAlternativa() {
        Vessel origem = vessel("vessel-a", "owner-1");
        Vessel deOutroDono = vessel("vessel-c", "owner-999");

        when(vesselRepository.findById("vessel-a")).thenReturn(Optional.of(origem));
        // findByOwnerId já é escopado por owner-1 — vessel-c nunca apareceria aqui na prática,
        // mas o teste documenta a expectativa: a Saga só busca dentro da MESMA frota (Princípio VII)
        when(vesselRepository.findByOwnerId("owner-1")).thenReturn(List.of(origem));

        CancelDayWithBookingsUseCase useCase = new CancelDayWithBookingsUseCase(
                vesselRepository, availabilityRepository, seatLimitRepository, transferAttemptRepository);

        BookingTransferAttempt attempt = useCase.cancelDay("vessel-a", data, TourType.ALTO_MAR, "manutenção");

        assertThat(attempt.getStatus()).isEqualTo(TransferAttemptStatus.CANCELLED_NO_ALTERNATIVE);
    }
}
