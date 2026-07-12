package com.empresa.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.empresa.booking.application.exception.InsufficientSeatsException;
import com.empresa.booking.application.exception.MinimumAdvancePurchaseException;
import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.seathold.SeatHold;
import com.empresa.booking.infrastructure.dynamodb.SeatCountRepository;
import com.empresa.booking.infrastructure.dynamodb.SeatHoldRepository;

/** T038 — FR-003 (overselling), FR-004 (retenção de 10 min), FR-014 (antecedência mínima de 24h). */
@ExtendWith(MockitoExtension.class)
class CreateHoldUseCaseTest {

    @Mock
    private SeatCountRepository seatCountRepository;

    @Mock
    private SeatHoldRepository seatHoldRepository;

    private final LocalDate dataFutura = LocalDate.now().plusDays(10);

    @Test
    void deveCriarHoldQuandoHaVagaDisponivel() {
        when(seatCountRepository.tryIncrementHeld("vessel-1", dataFutura, TourType.ALTO_MAR, 2)).thenReturn(true);

        CreateHoldUseCase useCase = new CreateHoldUseCase(seatCountRepository, seatHoldRepository, 15000L);
        CreateHoldResult result = useCase.createHold(new CreateHoldCommand("buyer-1", "vessel-1", dataFutura, TourType.ALTO_MAR, 2));

        assertThat(result.vesselId()).isEqualTo("vessel-1");
        assertThat(result.quantidade()).isEqualTo(2);
        assertThat(result.holdId()).isNotBlank();
        verify(seatHoldRepository).save(any(SeatHold.class));
    }

    @Test
    void deveRecusarQuandoNaoHaVagaDisponivel() {
        when(seatCountRepository.tryIncrementHeld("vessel-1", dataFutura, TourType.ALTO_MAR, 5)).thenReturn(false);

        CreateHoldUseCase useCase = new CreateHoldUseCase(seatCountRepository, seatHoldRepository, 15000L);

        assertThatThrownBy(() -> useCase.createHold(new CreateHoldCommand("buyer-1", "vessel-1", dataFutura, TourType.ALTO_MAR, 5)))
                .isInstanceOf(InsufficientSeatsException.class);
        verify(seatHoldRepository, never()).save(any());
    }

    @Test
    void deveRecusarComAntecedenciaMenorQueVinteEQuatroHoras() {
        CreateHoldUseCase useCase = new CreateHoldUseCase(seatCountRepository, seatHoldRepository, 15000L);
        LocalDate amanha = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> useCase.createHold(new CreateHoldCommand("buyer-1", "vessel-1", amanha, TourType.ALTO_MAR, 1)))
                .isInstanceOf(MinimumAdvancePurchaseException.class);
        verify(seatCountRepository, never()).tryIncrementHeld(any(), any(), any(), anyInt());
    }

    @Test
    void deveCompensarHeldSeSalvarHoldFalhar() {
        when(seatCountRepository.tryIncrementHeld("vessel-1", dataFutura, TourType.ALTO_MAR, 1)).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("falha ao salvar")).when(seatHoldRepository).save(any());

        CreateHoldUseCase useCase = new CreateHoldUseCase(seatCountRepository, seatHoldRepository, 15000L);

        assertThatThrownBy(() -> useCase.createHold(new CreateHoldCommand("buyer-1", "vessel-1", dataFutura, TourType.ALTO_MAR, 1)))
                .isInstanceOf(RuntimeException.class);
        verify(seatCountRepository, times(1)).decrementHeld(eq("vessel-1"), eq(dataFutura), eq(TourType.ALTO_MAR), eq(1));
    }
}
