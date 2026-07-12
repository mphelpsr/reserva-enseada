package com.empresa.vesselmanagement.infrastructure.dynamodb;

import java.time.LocalDate;
import java.util.List;
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

/**
 * T035b. VESSEL#{vesselId} / BOOKINGCOUNT#{data}#{tipoPasseio} — decisão de
 * 2026-07-12 em plan-vessel-management.md. Escrita restrita ao consumidor T059b
 * (evento `booking.confirmed`) e T059 (`booking.cancelled`); leitura usada por
 * SetAvailabilityUseCase (T041) e RemoveVesselUseCase (T040b).
 */
@Repository
public class BookingCountRepository {

    private final DynamoDbTable<ConfirmedBookingCount> table;

    public BookingCountRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
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
}
