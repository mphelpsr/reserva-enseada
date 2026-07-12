package com.empresa.vesselmanagement.application;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.empresa.vesselmanagement.application.exception.RotationAvailabilityConflictException;
import com.empresa.vesselmanagement.application.exception.VesselNotFoundException;
import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;
import com.empresa.vesselmanagement.domain.availability.RotationSchedule;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.vessel.Vessel;
import com.empresa.vesselmanagement.infrastructure.dynamodb.AvailabilityRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.RotationScheduleRepository;
import com.empresa.vesselmanagement.infrastructure.dynamodb.VesselRepository;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * T042. FR-013: rodízio bloqueia SÓ Alto Mar. FR-014: cadastrar rodízio num dia em
 * que Alto Mar já está disponível é rejeitado com 409 — mesmo conflito de
 * SetAvailabilityUseCase (T041), só que checado no sentido inverso (aqui é o
 * rodízio chegando depois). Desbloquear (bloqueado=false) nunca gera conflito.
 */
@Service
public class SetRotationScheduleUseCase {

    private final VesselRepository vesselRepository;
    private final RotationScheduleRepository rotationScheduleRepository;
    private final AvailabilityRepository availabilityRepository;
    private final DynamoDbEnhancedClient enhancedClient;

    public SetRotationScheduleUseCase(
            VesselRepository vesselRepository,
            RotationScheduleRepository rotationScheduleRepository,
            AvailabilityRepository availabilityRepository,
            DynamoDbEnhancedClient enhancedClient) {
        this.vesselRepository = vesselRepository;
        this.rotationScheduleRepository = rotationScheduleRepository;
        this.availabilityRepository = availabilityRepository;
        this.enhancedClient = enhancedClient;
    }

    public RotationSchedule setRotation(String vesselId, LocalDate data, boolean bloqueado) {
        vesselRepository.findById(vesselId).orElseThrow(() -> new VesselNotFoundException(vesselId));

        RotationSchedule rotation = RotationSchedule.builder()
                .vesselId(vesselId)
                .data(data)
                .bloqueado(bloqueado)
                .build();

        if (!bloqueado) {
            rotationScheduleRepository.save(rotation);
            return rotation;
        }

        Key availabilityKey = Key.builder()
                .partitionValue(Vessel.pkFor(vesselId))
                .sortValue(DeclaredAvailability.skFor(data, TourType.ALTO_MAR))
                .build();

        Expression altoMarNotAvailable = Expression.builder()
                .expression("attribute_not_exists(PK) OR disponivel = :false")
                .putExpressionValue(":false", AttributeValue.builder().bool(false).build())
                .build();

        try {
            enhancedClient.transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                    .addConditionCheck(availabilityRepository.table(), ConditionCheck.builder()
                            .key(availabilityKey)
                            .conditionExpression(altoMarNotAvailable)
                            .build())
                    .addPutItem(rotationScheduleRepository.table(), TransactPutItemEnhancedRequest.builder(RotationSchedule.class)
                            .item(rotation)
                            .build())
                    .build());
        } catch (TransactionCanceledException e) {
            throw new RotationAvailabilityConflictException(vesselId, data);
        }

        return rotation;
    }
}
