package com.empresa.vesselmanagement.application;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.empresa.vesselmanagement.application.exception.RotationAvailabilityConflictException;
import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;
import com.empresa.vesselmanagement.domain.availability.RotationSchedule;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.cancellation.BookingTransferAttempt;
import com.empresa.vesselmanagement.domain.cancellation.TransferAttemptStatus;
import com.empresa.vesselmanagement.infrastructure.dynamodb.AvailabilityRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.BookingCountRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.RotationScheduleRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;
import com.empresa.vesselmanagement.domain.vessel.Vessel;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * T041. FR-003, FR-004: a marcação do proprietário É a disponibilidade final.
 *
 * FR-014 (plan.md, access pattern 5): marcar Alto Mar disponível quando já existe
 * rodízio no mesmo dia é rejeitado com 409 — checado via `TransactWriteItems` com
 * `ConditionCheck` no item de rodízio, na mesma escrita da disponibilidade (evita
 * corrida entre leitura e escrita, Princípio II).
 *
 * FR-007 (Princípio VII): desmarcar um dia com reserva confirmada (réplica
 * `ConfirmedBookingCount`, ver plan.md — "Eventos Consumidos") não aplica a
 * mudança direto — delega para CancelDayWithBookingsUseCase (T046).
 */
@Service
public class SetAvailabilityUseCase {

    private final VesselRepository vesselRepository;
    private final AvailabilityRepository availabilityRepository;
    private final RotationScheduleRepository rotationScheduleRepository;
    private final BookingCountRepository bookingCountRepository;
    private final CancelDayWithBookingsUseCase cancelDayWithBookingsUseCase;
    private final DynamoDbEnhancedClient enhancedClient;

    public SetAvailabilityUseCase(
            VesselRepository vesselRepository,
            AvailabilityRepository availabilityRepository,
            RotationScheduleRepository rotationScheduleRepository,
            BookingCountRepository bookingCountRepository,
            CancelDayWithBookingsUseCase cancelDayWithBookingsUseCase,
            DynamoDbEnhancedClient enhancedClient) {
        this.vesselRepository = vesselRepository;
        this.availabilityRepository = availabilityRepository;
        this.rotationScheduleRepository = rotationScheduleRepository;
        this.bookingCountRepository = bookingCountRepository;
        this.cancelDayWithBookingsUseCase = cancelDayWithBookingsUseCase;
        this.enhancedClient = enhancedClient;
    }

    public SetAvailabilityResult setAvailability(
            String vesselId, LocalDate data, TourType tipoPasseio, boolean disponivel, String motivo) {
        vesselRepository.findById(vesselId).orElseThrow(() -> new VesselNotFoundException(vesselId));

        if (!disponivel) {
            boolean temReservaConfirmada = bookingCountRepository.findByVesselDateType(vesselId, data, tipoPasseio)
                    .map(count -> count.getCount() > 0)
                    .orElse(false);

            if (temReservaConfirmada) {
                BookingTransferAttempt attempt = cancelDayWithBookingsUseCase.cancelDay(vesselId, data, tipoPasseio, motivo);
                if (attempt.getStatus() == TransferAttemptStatus.VIABLE_PENDING) {
                    return new SetAvailabilityResult.TransferPending(attempt.getTargetVesselId());
                }
                return new SetAvailabilityResult.CancellationInitiated(DeclaredAvailability.builder()
                        .vesselId(vesselId).data(data).tipoPasseio(tipoPasseio).disponivel(false).motivo(motivo).build());
            }
        }

        DeclaredAvailability availability = DeclaredAvailability.builder()
                .vesselId(vesselId)
                .data(data)
                .tipoPasseio(tipoPasseio)
                .disponivel(disponivel)
                .motivo(motivo)
                .build();

        if (tipoPasseio == TourType.ALTO_MAR && disponivel) {
            saveWithRotationConflictCheck(vesselId, data, availability);
        } else {
            availabilityRepository.save(availability);
        }

        return new SetAvailabilityResult.Applied(availability);
    }

    private void saveWithRotationConflictCheck(String vesselId, LocalDate data, DeclaredAvailability availability) {
        Key rotationKey = Key.builder()
                .partitionValue(Vessel.pkFor(vesselId))
                .sortValue(RotationSchedule.skFor(data))
                .build();

        Expression rotationNotBlocked = Expression.builder()
                .expression("attribute_not_exists(PK) OR bloqueado = :false")
                .putExpressionValue(":false", AttributeValue.builder().bool(false).build())
                .build();

        try {
            enhancedClient.transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                    .addConditionCheck(rotationScheduleRepository.table(), ConditionCheck.builder()
                            .key(rotationKey)
                            .conditionExpression(rotationNotBlocked)
                            .build())
                    .addPutItem(availabilityRepository.table(), TransactPutItemEnhancedRequest.builder(DeclaredAvailability.class)
                            .item(availability)
                            .build())
                    .build());
        } catch (TransactionCanceledException e) {
            throw new RotationAvailabilityConflictException(vesselId, data);
        }
    }
}
