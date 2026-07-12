package com.empresa.vesselmanagement.infrastructure.dynamodb;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.vesselmanagement.domain.availability.DeclaredAvailability;
import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.vessel.Vessel;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

/** T033. VESSEL#{vesselId} / AVAIL#{data}#{tipoPasseio} — ver plan.md (Data Model). */
@Repository
public class AvailabilityRepository {

    private final DynamoDbTable<DeclaredAvailability> table;

    public AvailabilityRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(DeclaredAvailability.class));
    }

    /** Exposto para os casos de uso componerem TransactWriteItems com RotationScheduleRepository (FR-014). */
    public DynamoDbTable<DeclaredAvailability> table() {
        return table;
    }

    public Optional<DeclaredAvailability> findByVesselDateType(String vesselId, LocalDate data, TourType tipoPasseio) {
        return Optional.ofNullable(table.getItem(Key.builder()
                .partitionValue(Vessel.pkFor(vesselId))
                .sortValue(DeclaredAvailability.skFor(data, tipoPasseio))
                .build()));
    }

    public List<DeclaredAvailability> findByVesselAndDateRange(String vesselId, LocalDate from, LocalDate to) {
        return table.query(QueryConditional.sortBetween(
                        Key.builder().partitionValue(Vessel.pkFor(vesselId)).sortValue("AVAIL#" + from).build(),
                        Key.builder().partitionValue(Vessel.pkFor(vesselId)).sortValue("AVAIL#" + to + "~").build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    public void save(DeclaredAvailability availability) {
        table.putItem(availability);
    }
}
