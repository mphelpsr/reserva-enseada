package com.empresa.vesselmanagement.domain.seatlimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T060 — FR-015: o padrão automático de 10% só pode ser aplicado 2 vezes por embarcação. */
class DefaultSeatUsageCounterTest {

    @Test
    void podeAplicarPadraoAntesDoLimite() {
        var counter = DefaultSeatUsageCounter.builder().vesselId("vessel-1").vezesAplicado(0).build();
        assertThat(counter.podeAplicarPadrao()).isTrue();

        counter.setVezesAplicado(1);
        assertThat(counter.podeAplicarPadrao()).isTrue();
    }

    @Test
    void naoPodeAplicarPadraoNoLimiteOuAcima() {
        var noLimite = DefaultSeatUsageCounter.builder().vesselId("vessel-1").vezesAplicado(2).build();
        assertThat(noLimite.podeAplicarPadrao()).isFalse();

        var acimaDoLimite = DefaultSeatUsageCounter.builder().vesselId("vessel-1").vezesAplicado(3).build();
        assertThat(acimaDoLimite.podeAplicarPadrao()).isFalse();
    }
}
