package com.empresa.vesselmanagement.domain.bookingcount;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.empresa.vesselmanagement.domain.availability.TourType;

/** T060 — decisão de 2026-07-12 (plan.md): base de FR-004 vs FR-007. */
class ConfirmedBookingCountTest {

    @Test
    void temReservaConfirmadaQuandoContadorMaiorQueZero() {
        var count = ConfirmedBookingCount.builder()
                .vesselId("vessel-1").data(LocalDate.now()).tipoPasseio(TourType.ALTO_MAR).count(1).build();
        assertThat(count.temReservaConfirmada()).isTrue();
    }

    @Test
    void naoTemReservaConfirmadaQuandoContadorZero() {
        var count = ConfirmedBookingCount.builder()
                .vesselId("vessel-1").data(LocalDate.now()).tipoPasseio(TourType.ALTO_MAR).count(0).build();
        assertThat(count.temReservaConfirmada()).isFalse();
    }
}
