package com.empresa.vesselmanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.empresa.vesselmanagement.application.event.VesselRecebedorChangedEvent;
import com.empresa.vesselmanagement.application.exception.InvalidPaymentPixKeyException;
import com.empresa.vesselmanagement.domain.vessel.Owner;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.OwnerRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

/** T059c — FR-016 (revisão de 2026-07-12): payment_recebedor_id agora é a chave Pix do proprietário. */
@ExtendWith(MockitoExtension.class)
class SetPaymentPixKeyUseCaseTest {

    @Mock
    private OwnerRepository ownerRepository;

    @Mock
    private VesselRepository vesselRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SetPaymentPixKeyUseCase useCase() {
        return new SetPaymentPixKeyUseCase(ownerRepository, vesselRepository, eventPublisher);
    }

    @Test
    void gravaAChaveEPublicaUmEventoPorEmbarcacaoDoProprietario() {
        when(ownerRepository.findById("owner-1")).thenReturn(Optional.of(Owner.builder().id("owner-1").build()));
        when(vesselRepository.findByOwnerId("owner-1")).thenReturn(List.of(
                Vessel.builder().id("vessel-1").ownerId("owner-1").nomeLegal("A").capacidadeMaxima(10)
                        .portoSaida("Porto A").status(VesselStatus.ATIVA).build(),
                Vessel.builder().id("vessel-2").ownerId("owner-1").nomeLegal("B").capacidadeMaxima(10)
                        .portoSaida("Porto A").status(VesselStatus.ATIVA).build()));

        Owner result = useCase().setPaymentPixKey("owner-1", "chave-pix-123");

        assertThat(result.getPaymentRecebedorId()).isEqualTo("chave-pix-123");
        ArgumentCaptor<Owner> ownerCaptor = ArgumentCaptor.forClass(Owner.class);
        verify(ownerRepository).save(ownerCaptor.capture());
        assertThat(ownerCaptor.getValue().getPaymentRecebedorId()).isEqualTo("chave-pix-123");

        verify(eventPublisher, times(2)).publishEvent(any(VesselRecebedorChangedEvent.class));
        verify(eventPublisher).publishEvent(new VesselRecebedorChangedEvent("vessel-1", "chave-pix-123"));
        verify(eventPublisher).publishEvent(new VesselRecebedorChangedEvent("vessel-2", "chave-pix-123"));
    }

    @Test
    void criaOProprietarioSeAindaNaoExistir() {
        when(ownerRepository.findById("owner-novo")).thenReturn(Optional.empty());
        when(vesselRepository.findByOwnerId("owner-novo")).thenReturn(List.of());

        Owner result = useCase().setPaymentPixKey("owner-novo", "chave-pix-456");

        assertThat(result.getId()).isEqualTo("owner-novo");
        assertThat(result.getPaymentRecebedorId()).isEqualTo("chave-pix-456");
        verify(ownerRepository).save(result);
    }

    @Test
    void naoPublicaEventoQuandoProprietarioNaoTemEmbarcacoes() {
        when(ownerRepository.findById("owner-1")).thenReturn(Optional.of(Owner.builder().id("owner-1").build()));
        when(vesselRepository.findByOwnerId("owner-1")).thenReturn(List.of());

        useCase().setPaymentPixKey("owner-1", "chave-pix-123");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deveRecusarChavePixVazia() {
        assertThatThrownBy(() -> useCase().setPaymentPixKey("owner-1", ""))
                .isInstanceOf(InvalidPaymentPixKeyException.class);
    }

    @Test
    void deveRecusarChavePixNula() {
        assertThatThrownBy(() -> useCase().setPaymentPixKey("owner-1", null))
                .isInstanceOf(InvalidPaymentPixKeyException.class);
    }
}
