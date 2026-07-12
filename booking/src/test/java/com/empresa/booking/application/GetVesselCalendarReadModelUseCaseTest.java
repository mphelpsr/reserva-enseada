package com.empresa.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.empresa.booking.application.exception.VesselNotFoundException;
import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.seathold.SeatCount;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;

/** T045 — FR-001/FR-002: réplica local de disponibilidade/vagas restantes, sem cálculo próprio. */
@ExtendWith(MockitoExtension.class)
class GetVesselCalendarReadModelUseCaseTest {

    @Mock
    private SeatCountRepository seatCountRepository;

    @Test
    void deveRefletirDisponibilidadeEVagasRestantesReplicadas() {
        LocalDate data = LocalDate.of(2026, 12, 20);
        SeatCount altoMar = SeatCount.builder()
                .vesselId("vessel-1").data(data).tipoPasseio(TourType.ALTO_MAR)
                .limite(10).sold(3).held(2).disponivel(true).motivo(null).build();
        when(seatCountRepository.existsForVessel("vessel-1")).thenReturn(true);
        when(seatCountRepository.findByVesselDateType("vessel-1", data, TourType.ALTO_MAR)).thenReturn(Optional.of(altoMar));
        when(seatCountRepository.findByVesselDateType("vessel-1", data, TourType.ORLA)).thenReturn(Optional.empty());

        GetVesselCalendarReadModelUseCase useCase = new GetVesselCalendarReadModelUseCase(seatCountRepository);
        VesselCalendar calendar = useCase.getCalendar("vessel-1", data, data);

        assertThat(calendar.dias()).hasSize(1);
        VesselCalendar.Dia dia = calendar.dias().get(0);
        assertThat(dia.altoMar().disponivel()).isTrue();
        assertThat(dia.altoMar().vagasRestantes()).isEqualTo(5); // 10 - 3 - 2
        assertThat(dia.orla().disponivel()).isFalse();
        assertThat(dia.orla().vagasRestantes()).isZero();
    }

    @Test
    void nuncaSobrescreveDisponibilidadeComBaseEmMotivoDeMare() {
        LocalDate data = LocalDate.of(2026, 12, 20);
        SeatCount comAlerta = SeatCount.builder()
                .vesselId("vessel-1").data(data).tipoPasseio(TourType.ALTO_MAR)
                .limite(10).sold(0).held(0).disponivel(true).motivo("maré alta prevista").build();
        when(seatCountRepository.existsForVessel("vessel-1")).thenReturn(true);
        when(seatCountRepository.findByVesselDateType("vessel-1", data, TourType.ALTO_MAR)).thenReturn(Optional.of(comAlerta));
        when(seatCountRepository.findByVesselDateType("vessel-1", data, TourType.ORLA)).thenReturn(Optional.empty());

        GetVesselCalendarReadModelUseCase useCase = new GetVesselCalendarReadModelUseCase(seatCountRepository);
        VesselCalendar calendar = useCase.getCalendar("vessel-1", data, data);

        VesselCalendar.DiaTipoPasseio altoMar = calendar.dias().get(0).altoMar();
        assertThat(altoMar.disponivel()).isTrue();
        assertThat(altoMar.motivo()).isEqualTo("maré alta prevista");
    }

    @Test
    void deveLancarNotFoundParaEmbarcacaoNuncaAnunciada() {
        LocalDate data = LocalDate.of(2026, 12, 20);
        when(seatCountRepository.existsForVessel("inexistente")).thenReturn(false);

        GetVesselCalendarReadModelUseCase useCase = new GetVesselCalendarReadModelUseCase(seatCountRepository);

        assertThatThrownBy(() -> useCase.getCalendar("inexistente", data, data)).isInstanceOf(VesselNotFoundException.class);
    }
}
