package com.empresa.booking.domain.cancellation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

/** FR-006/FR-007 — modelo binário: dentro das duas janelas ao mesmo tempo, ou fora. */
class CancellationPolicyTest {

    @Test
    void dentroDeSeteDiasDaCompraEQuarentaEOitoHorasDoPasseioPermiteCancelamento() {
        Instant compradaEm = Instant.now();
        LocalDate dataPasseio = LocalDate.now().plusDays(30);

        assertThat(CancellationPolicy.dentroDaJanela(compradaEm, dataPasseio, Instant.now())).isTrue();
    }

    @Test
    void aposSeteDiasDaCompraRecusaMesmoComPasseioDistante() {
        Instant compradaEm = Instant.now().minus(8, ChronoUnit.DAYS);
        LocalDate dataPasseio = LocalDate.now().plusDays(30);

        assertThat(CancellationPolicy.dentroDaJanela(compradaEm, dataPasseio, Instant.now())).isFalse();
    }

    @Test
    void comMenosDeQuarentaEOitoHorasDoPasseioRecusaMesmoDentroDeSeteDiasDaCompra() {
        Instant compradaEm = Instant.now();
        LocalDate dataPasseio = LocalDate.now().plusDays(1);

        assertThat(CancellationPolicy.dentroDaJanela(compradaEm, dataPasseio, Instant.now())).isFalse();
    }

    @Test
    void permiteNoLimiteExatoDasDuasJanelas() {
        Instant agora = Instant.parse("2026-01-01T00:00:00Z");
        Instant compradaEm = agora.minus(7, ChronoUnit.DAYS);
        LocalDate dataPasseio = agora.plus(48, ChronoUnit.HOURS).atZone(ZoneOffset.UTC).toLocalDate();

        assertThat(CancellationPolicy.dentroDaJanela(compradaEm, dataPasseio, agora)).isTrue();
    }
}
