package com.empresa.vesselmanagement.infrastructure.dynamodb;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.vesselmanagement.domain.availability.RotationSchedule;
import com.empresa.vesselmanagement.domain.vessel.Vessel;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/** T034. VESSEL#{vesselId} / ROTATION#{data} — ver plan.md (Data Model). */
@Repository
public class RotationScheduleRepository {

    private final DynamoDbTable<RotationSchedule> table;

    public RotationScheduleRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(RotationSchedule.class));
    }

    /** Exposto para os casos de uso componerem TransactWriteItems com AvailabilityRepository (FR-014). */
    public DynamoDbTable<RotationSchedule> table() {
        return table;
    }

    public Optional<RotationSchedule> findByVesselDate(String vesselId, LocalDate data) {
        return Optional.ofNullable(table.getItem(Key.builder()
                .partitionValue(Vessel.pkFor(vesselId))
                .sortValue(RotationSchedule.skFor(data))
                .build()));
    }

    public void save(RotationSchedule rotationSchedule) {
        table.putItem(rotationSchedule);
    }
}
