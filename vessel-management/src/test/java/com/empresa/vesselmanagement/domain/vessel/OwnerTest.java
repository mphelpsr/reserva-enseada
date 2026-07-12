package com.empresa.vesselmanagement.domain.vessel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** T060 — FR-016: portão de ativação de embarcação. */
class OwnerTest {

    @Test
    void invalidoQuandoPaymentRecebedorIdNulo() {
        var owner = Owner.builder().id("owner-1").paymentRecebedorId(null).build();
        assertThat(owner.hasValidPaymentRecebedorId()).isFalse();
    }

    @Test
    void invalidoQuandoPaymentRecebedorIdEmBranco() {
        var owner = Owner.builder().id("owner-1").paymentRecebedorId("   ").build();
        assertThat(owner.hasValidPaymentRecebedorId()).isFalse();
    }

    @Test
    void validoQuandoPaymentRecebedorIdPreenchido() {
        var owner = Owner.builder().id("owner-1").paymentRecebedorId("recebedor-123").build();
        assertThat(owner.hasValidPaymentRecebedorId()).isTrue();
    }
}
