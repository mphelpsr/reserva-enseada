package com.empresa.booking.infrastructure.dynamodb.converter;

import com.empresa.booking.domain.booking.BookingStatus;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Mesma razão de TourTypeConverter — mantém `status` em DynamoDB no valor lowercase de BookingStatus.getValue(). */
public class BookingStatusConverter implements AttributeConverter<BookingStatus> {

    @Override
    public AttributeValue transformFrom(BookingStatus input) {
        return AttributeValue.builder().s(input.getValue()).build();
    }

    @Override
    public BookingStatus transformTo(AttributeValue input) {
        return BookingStatus.fromValue(input.s());
    }

    @Override
    public EnhancedType<BookingStatus> type() {
        return EnhancedType.of(BookingStatus.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
