package com.empresa.vesselmanagement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.empresa.vesselmanagement.application.exception.RotationAvailabilityConflictException;
import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;
import com.empresa.vesselmanagement.domain.availability.RotationSchedule;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.domain.vessel.VesselStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.AvailabilityRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingCountRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.RotationScheduleRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * T041 — FR-014: marcar Alto Mar disponível quando já existe rodízio no mesmo dia
 * é rejeitado com 409 (RotationAvailabilityConflictException), traduzido a partir
 * do TransactionCanceledException que o DynamoDB devolve quando o ConditionCheck
 * do item de rodízio falha (ver saveWithRotationConflictCheck).
 */
@ExtendWith(MockitoExtension.class)
class SetAvailabilityUseCaseTest {

    @Mock
    private VesselRepository vesselRepository;
    @Mock
    private AvailabilityRepository availabilityRepository;
    @Mock
    private RotationScheduleRepository rotationScheduleRepository;
    @Mock
    private BookingCountRepository bookingCountRepository;
    @Mock
    private CancelDayWithBookingsUseCase cancelDayWithBookingsUseCase;
    @Mock
    private DynamoDbEnhancedClient enhancedClient;
    @Mock
    private DynamoDbTable<DeclaredAvailability> availabilityTable;
    @Mock
    private DynamoDbTable<RotationSchedule> rotationTable;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final LocalDate data = LocalDate.of(2026, 10, 5);
    private final Vessel vessel = Vessel.builder()
            .id("vessel-1").ownerId("owner-1").nomeLegal("Nome").capacidadeMaxima(20)
            .portoSaida("Porto A").status(VesselStatus.ATIVA).build();

    private SetAvailabilityUseCase useCase() {
        return new SetAvailabilityUseCase(
                vesselRepository, availabilityRepository, rotationScheduleRepository,
                bookingCountRepository, cancelDayWithBookingsUseCase, enhancedClient, eventPublisher);
    }

    @Test
    void deveLancarConflitoQuandoTransacaoECanceladaPeloRodizio() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));
        doReturn(rotationTable).when(rotationScheduleRepository).table();
        doReturn(availabilityTable).when(availabilityRepository).table();
        doReturn(TableSchema.fromBean(RotationSchedule.class)).when(rotationTable).tableSchema();
        doReturn(TableSchema.fromBean(DeclaredAvailability.class)).when(availabilityTable).tableSchema();
        doThrow(TransactionCanceledException.builder()
                .cancellationReasons(CancellationReason.builder().code("ConditionalCheckFailed").build())
                .build())
                .when(enhancedClient).transactWriteItems(any(TransactWriteItemsEnhancedRequest.class));

        assertThatThrownBy(() -> useCase().setAvailability("vessel-1", data, TourType.ALTO_MAR, true, null))
                .isInstanceOf(RotationAvailabilityConflictException.class);
    }

    @Test
    void deveAplicarNormalmenteQuandoTransacaoTemSucesso() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));
        doReturn(rotationTable).when(rotationScheduleRepository).table();
        doReturn(availabilityTable).when(availabilityRepository).table();
        doReturn(TableSchema.fromBean(RotationSchedule.class)).when(rotationTable).tableSchema();
        doReturn(TableSchema.fromBean(DeclaredAvailability.class)).when(availabilityTable).tableSchema();

        SetAvailabilityResult result = useCase().setAvailability("vessel-1", data, TourType.ALTO_MAR, true, null);

        assertThat(result).isInstanceOf(SetAvailabilityResult.Applied.class);
        assertThat(((SetAvailabilityResult.Applied) result).availability().isDisponivel()).isTrue();
    }

    @Test
    void tipoOrlaNaoPassaPeloConflitoDeRodizioENaoUsaTransacao() {
        when(vesselRepository.findById("vessel-1")).thenReturn(Optional.of(vessel));

        SetAvailabilityResult result = useCase().setAvailability("vessel-1", data, TourType.ORLA, true, null);

        assertThat(result).isInstanceOf(SetAvailabilityResult.Applied.class);
    }
}
