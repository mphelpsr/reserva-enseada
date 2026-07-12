package com.empresa.booking.infrastructure.dynamodb.converter;

import com.empresa.booking.domain.availability.TourType;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Sem isto, o Enhanced Client usa `Enum.toString()` (ex.: "ALTO_MAR") para
 * persistir — divergente do valor lowercase ("alto_mar") já usado em todo o
 * resto do sistema (payload JSON via @JsonValue em TourType, eventos
 * SNS/SQS consumidos do vessel-management, fixtures de teste com o
 * DynamoDbClient cru). Este converter alinha a representação em DynamoDB
 * com essa mesma convenção, via TourType.getValue()/fromValue().
 */
public class TourTypeConverter implements AttributeConverter<TourType> {

    @Override
    public AttributeValue transformFrom(TourType input) {
        return AttributeValue.builder().s(input.getValue()).build();
    }

    @Override
    public TourType transformTo(AttributeValue input) {
        return TourType.fromValue(input.s());
    }

    @Override
    public EnhancedType<TourType> type() {
        return EnhancedType.of(TourType.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
