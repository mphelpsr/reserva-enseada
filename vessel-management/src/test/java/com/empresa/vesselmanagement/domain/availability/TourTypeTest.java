package com.empresa.vesselmanagement.domain.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** T060 — conversão dos valores em minúsculo usados nas rotas/JSON (FR-012). */
class TourTypeTest {

    @Test
    void fromValueReconheceAltoMarEOrla() {
        assertThat(TourType.fromValue("alto_mar")).isEqualTo(TourType.ALTO_MAR);
        assertThat(TourType.fromValue("orla")).isEqualTo(TourType.ORLA);
    }

    @Test
    void getValueDevolveARepresentacaoEmMinusculo() {
        assertThat(TourType.ALTO_MAR.getValue()).isEqualTo("alto_mar");
        assertThat(TourType.ORLA.getValue()).isEqualTo("orla");
    }

    @Test
    void fromValueRecusaValorInvalido() {
        assertThatThrownBy(() -> TourType.fromValue("passeio-invalido"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
