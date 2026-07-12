package com.empresa.booking.domain.seathold;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.empresa.booking.domain.availability.TourType;

/** T059 — FR-013, Opção C: `vagasRestantes` nunca fica negativo, mesmo com redução retroativa do limite. */
class SeatCountTest {

    private SeatCount seatCount(int limite, int sold, int held) {
        return SeatCount.builder()
                .vesselId("vessel-1").data(LocalDate.now().plusDays(10)).tipoPasseio(TourType.ALTO_MAR)
                .limite(limite).sold(sold).held(held).disponivel(true)
                .build();
    }

    @Test
    void subtraiVendidasERetidasDoLimite() {
        assertThat(seatCount(10, 3, 2).vagasRestantes()).isEqualTo(5);
    }

    @Test
    void zeraQuandoVendidasEsgotamOLimite() {
        assertThat(seatCount(10, 10, 0).vagasRestantes()).isZero();
    }

    @Test
    void nuncaFicaNegativoQuandoProprietarioReduzLimiteAbaixoDoJaVendido() {
        // FR-015 do vessel-management: redução do limite nunca invalida reservas existentes,
        // então sold+held pode legitimamente ultrapassar o novo limite.
        assertThat(seatCount(3, 5, 2).vagasRestantes()).isZero();
    }

    @Test
    void contaSomenteRetidasQuandoNaoHaVendidas() {
        assertThat(seatCount(10, 0, 4).vagasRestantes()).isEqualTo(6);
    }
}
