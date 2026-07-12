package com.empresa.vesselmanagement.infrastructure.dynamodb;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.vesselmanagement.domain.advisory.WeatherTideAdvisory;
import com.empresa.vesselmanagement.domain.vessel.Vessel;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * T036. VESSEL#{vesselId} / ADVISORY#{data} — ver plan.md (Data Model). Leitura para
 * a API (GetAdvisoryUseCase, T045); escrita restrita ao job assíncrono (T057), que
 * ainda não existe nesta fase — `save` aqui só está exposto para T057 usar depois.
 */
@Repository
public class AdvisoryRepository {

    private final DynamoDbTable<WeatherTideAdvisory> table;

    public AdvisoryRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(WeatherTideAdvisory.class));
    }

    public Optional<WeatherTideAdvisory> findByVesselDate(String vesselId, LocalDate data) {
        return Optional.ofNullable(table.getItem(Key.builder()
                .partitionValue(Vessel.pkFor(vesselId))
                .sortValue(WeatherTideAdvisory.skFor(data))
                .build()));
    }

    public void save(WeatherTideAdvisory advisory) {
        table.putItem(advisory);
    }
}
