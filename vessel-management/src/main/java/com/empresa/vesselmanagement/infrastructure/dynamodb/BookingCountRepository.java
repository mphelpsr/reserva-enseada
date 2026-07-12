package com.empresa.vesselmanagement.infrastructure.dynamodb;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.bookingcount.ConfirmedBookingCount;
import com.empresa.vesselmanagement.domain.vessel.Vessel;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * T035b. VESSEL#{vesselId} / BOOKINGCOUNT#{data}#{tipoPasseio} — decisão de
 * 2026-07-12 em plan-vessel-management.md. Escrita restrita ao consumidor T059b
 * (evento `booking.confirmed`) e T059 (`booking.cancelled`); leitura usada por
 * SetAvailabilityUseCase (T041) e RemoveVesselUseCase (T040b).
 */
@Repository
public class BookingCountRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final DynamoDbTable<ConfirmedBookingCount> table;

    public BookingCountRepository(
            DynamoDbEnhancedClient enhancedClient, DynamoDbClient dynamoDbClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(ConfirmedBookingCount.class));
    }

    public Optional<ConfirmedBookingCount> findByVesselDateType(String vesselId, LocalDate data, TourType tipoPasseio) {
        return Optional.ofNullable(table.getItem(Key.builder()
                .partitionValue(Vessel.pkFor(vesselId))
                .sortValue(ConfirmedBookingCount.skFor(data, tipoPasseio))
                .build()));
    }

    /** FR-002/T040b: soma todas as reservas futuras confirmadas de uma embarcação, qualquer dia/tipo. */
    public List<ConfirmedBookingCount> findFutureBookingsForVessel(String vesselId, LocalDate today) {
        return table.query(QueryConditional.sortBeginsWith(
                        Key.builder().partitionValue(Vessel.pkFor(vesselId)).sortValue("BOOKINGCOUNT#").build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(item -> !item.getData().isBefore(today) && item.temReservaConfirmada())
                .toList();
    }

    public void save(ConfirmedBookingCount count) {
        table.putItem(count);
    }

    /** T059b (evento `booking.confirmed`): incrementa atomicamente via UpdateItem (ADD). */
    public void increment(String vesselId, LocalDate data, TourType tipoPasseio) {
        addToCount(vesselId, data, tipoPasseio, 1);
    }

    /**
     * T059 (evento `booking.cancelled`): decrementa atomicamente, nunca abaixo de zero.
     * Se o item já estiver em zero (ou não existir), a condição falha e é ignorada —
     * simplificação aceitável (Princípio VI): eventos fora de ordem/duplicados na pior
     * hipótese deixam o contador zerado, nunca negativo.
     */
    public void decrement(String vesselId, LocalDate data, TourType tipoPasseio) {
        try {
            addToCount(vesselId, data, tipoPasseio, -1);
        } catch (ConditionalCheckFailedException ignored) {
            // contador já em zero — nada a decrementar
        }
    }

    private void addToCount(String vesselId, LocalDate data, TourType tipoPasseio, int delta) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", AttributeValue.builder().s(Vessel.pkFor(vesselId)).build());
        key.put("SK", AttributeValue.builder().s(ConfirmedBookingCount.skFor(data, tipoPasseio)).build());

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":delta", AttributeValue.builder().n(String.valueOf(delta)).build());
        values.put(":zero", AttributeValue.builder().n("0").build());

        var builder = software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET #c = if_not_exists(#c, :zero) + :delta")
                .expressionAttributeNames(Map.of("#c", "count"))
                .expressionAttributeValues(values);

        if (delta < 0) {
            builder.conditionExpression("if_not_exists(#c, :zero) > :zero");
        }

        dynamoDbClient.updateItem(builder.build());
    }
}
