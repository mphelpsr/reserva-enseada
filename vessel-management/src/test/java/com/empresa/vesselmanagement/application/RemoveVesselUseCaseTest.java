package com.empresa.vesselmanagement.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.empresa.vesselmanagement.application.exception.VesselHasFutureBookingsException;
import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.bookingcount.ConfirmedBookingCount;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingCountRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/** T040b — FR-002: remoção exige transferência prévia com reservas futuras. */
@ExtendWith(MockitoExtension.class)
class RemoveVesselUseCaseTest {

    @Mock
    private VesselRepository vesselRepository;

    @Mock
    private BookingCountRepository bookingCountRepository;

    private final Vessel vessel = Vessel.builder()
            .id("vessel-1").ownerId("owner-1").nomeLegal("Nome").capacidadeMaxima(20)
            .portoSaida("Porto A").status(VesselStatus.ATIVA).build();

    @Test
    void deveRemoverDiretamenteSemReservasFuturas() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));
        when(bookingCountRepository.findFutureBookingsForVessel("vessel-1", LocalDate.now())).thenReturn(List.of());

        new RemoveVesselUseCase(vesselRepository, bookingCountRepository).remove("vessel-1");

        verify(vesselRepository).delete("vessel-1");
    }

    @Test
    void deveRecusarRemocaoComReservaFuturaConfirmada() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));
        when(bookingCountRepository.findFutureBookingsForVessel("vessel-1", LocalDate.now())).thenReturn(List.of(
                ConfirmedBookingCount.builder().vesselId("vessel-1").data(LocalDate.now().plusDays(5))
                        .tipoPasseio(TourType.ALTO_MAR).count(2).build()));

        RemoveVesselUseCase useCase = new RemoveVesselUseCase(vesselRepository, bookingCountRepository);

        assertThatThrownBy(() -> useCase.remove("vessel-1")).isInstanceOf(VesselHasFutureBookingsException.class);
    }

    @Test
    void deveLancarNotFoundParaEmbarcacaoInexistente() {
        when(vesselRepository.findById("inexistente")).thenReturn(Optional.empty());

        RemoveVesselUseCase useCase = new RemoveVesselUseCase(vesselRepository, bookingCountRepository);

        assertThatThrownBy(() -> useCase.remove("inexistente")).isInstanceOf(VesselNotFoundException.class);
    }
}
