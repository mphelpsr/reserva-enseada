package com.empresa.vesselmanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.empresa.vesselmanagement.application.exception.RotationAvailabilityConflictException;
import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;
import com.empresa.vesselmanagement.domain.availability.RotationSchedule;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.AvailabilityRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.RotationScheduleRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * T042/T060 — FR-014, sentido inverso de SetAvailabilityUseCaseTest: cadastrar
 * rodízio quando Alto Mar já está disponível no mesmo dia é rejeitado com 409,
 * traduzido do TransactionCanceledException do ConditionCheck sobre o item de
 * disponibilidade. Desbloquear rodízio (bloqueado=false) nunca usa transação.
 */
@ExtendWith(MockitoExtension.class)
class SetRotationScheduleUseCaseTest {

    @Mock
    private VesselRepository vesselRepository;
    @Mock
    private RotationScheduleRepository rotationScheduleRepository;
    @Mock
    private AvailabilityRepository availabilityRepository;
    @Mock
    private DynamoDbEnhancedClient enhancedClient;
    @Mock
    private DynamoDbTable<RotationSchedule> rotationTable;
    @Mock
    private DynamoDbTable<DeclaredAvailability> availabilityTable;

    private final LocalDate data = LocalDate.of(2026, 9, 1);
    private final Vessel vessel = Vessel.builder()
            .id("vessel-1").ownerId("owner-1").nomeLegal("Nome").capacidadeMaxima(20)
            .portoSaida("Porto A").status(VesselStatus.ATIVA).build();

    private SetRotationScheduleUseCase useCase() {
        return new SetRotationScheduleUseCase(vesselRepository, rotationScheduleRepository, availabilityRepository, enhancedClient);
    }

    @Test
    void deveLancarConflitoQuandoAltoMarJaDisponivel() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));
        doReturn(availabilityTable).when(availabilityRepository).table();
        doReturn(rotationTable).when(rotationScheduleRepository).table();
        doReturn(TableSchema.fromBean(DeclaredAvailability.class)).when(availabilityTable).tableSchema();
        doReturn(TableSchema.fromBean(RotationSchedule.class)).when(rotationTable).tableSchema();
        doThrow(TransactionCanceledException.builder()
                .cancellationReasons(CancellationReason.builder().code("ConditionalCheckFailed").build())
                .build())
                .when(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));

        assertThatThrownBy(() -> useCase().setRotation("vessel-1", data, true))
                .isInstanceOf(RotationAvailabilityConflictException.class);
    }

    @Test
    void deveCadastrarRodizioQuandoNaoHaConflito() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));
        doReturn(availabilityTable).when(availabilityRepository).table();
        doReturn(rotationTable).when(rotationScheduleRepository).table();
        doReturn(TableSchema.fromBean(DeclaredAvailability.class)).when(availabilityTable).tableSchema();
        doReturn(TableSchema.fromBean(RotationSchedule.class)).when(rotationTable).tableSchema();

        RotationSchedule result = useCase().setRotation("vessel-1", data, true);

        assertThat(result.isBloqueado()).isTrue();
        assertThat(result.getVesselId()).isEqualTo("vessel-1");
    }

    @Test
    void desbloquearRodizioNuncaUsaTransacaoNemGeraConflito() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));

        RotationSchedule result = useCase().setRotation("vessel-1", data, false);

        assertThat(result.isBloqueado()).isFalse();
        verify(rotationScheduleRepository).save(result);
    }

    @Test
    void deveLancarNotFoundParaEmbarcacaoInexistente() {
        when(vesselRepository.findById("inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().setRotation("inexistente", data, true))
                .isInstanceOf(VesselNotFoundException.class);
    }
}
