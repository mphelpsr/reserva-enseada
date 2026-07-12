package com.empresa.vesselmanagement.infrastructure.dynamodb;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.vesselmanagement.domain.availability.TourType;
import com.empresa.vesselmanagement.domain.seatlimit.DefaultSeatUsageCounter;
import com.empresa.vesselmanagement.domain.seatlimit.PlatformSeatLimit;
import com.empresa.vesselmanagement.domain.vessel.Vessel;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

/**
 * T035. VESSEL#{vesselId} / SEATLIMIT#{data}#{tipoPasseio} + COUNTER#DEFAULTSEAT
 * (plan.md — Data Model). FR-015.
 *
 * O incremento do contador usa `UpdateItem` de baixo nível com `ADD` (atômico) —
 * a Enhanced Client não expõe incremento parcial para beans. Não é feito na mesma
 * transação da escrita do `PlatformSeatLimit` (Opção C: a operação nunca bloqueia
 * o proprietário, então uma falha isolada no incremento não pode reverter a
 * definição do limite em si — pior caso é o contador ficar levemente atrasado,
 * nunca a venda de vaga).
 */
@Repository
public class SeatLimitRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final DynamoDbTable<PlatformSeatLimit> seatLimitTable;
    private final DynamoDbTable<DefaultSeatUsageCounter> counterTable;

    public SeatLimitRepository(
            DynamoDbEnhancedClient enhancedClient,
            DynamoDbClient dynamoDbClient,
            @Value("${app.dynamodb.table-name}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
        this.seatLimitTable = enhancedClient.table(tableName, TableSchema.fromBean(PlatformSeatLimit.class));
        this.counterTable = enhancedClient.table(tableName, TableSchema.fromBean(DefaultSeatUsageCounter.class));
    }

    public Optional<PlatformSeatLimit> findByVesselDateType(String vesselId, LocalDate data, TourType tipoPasseio) {
        return Optional.ofNullable(seatLimitTable.getItem(Key.builder()
                .partitionValue(Vessel.pkFor(vesselId))
                .sortValue(PlatformSeatLimit.skFor(data, tipoPasseio))
                .build()));
    }

    public void save(PlatformSeatLimit seatLimit) {
        seatLimitTable.putItem(seatLimit);
    }

    public DefaultSeatUsageCounter getCounter(String vesselId) {
        DefaultSeatUsageCounter counter = counterTable.getItem(Key.builder()
                .partitionValue(Vessel.pkFor(vesselId))
                .sortValue(DefaultSeatUsageCounter.SK)
                .build());
        return counter != null ? counter : DefaultSeatUsageCounter.builder().vesselId(vesselId).vezesAplicado(0).build();
    }

    /** Incrementa atomicamente e retorna o valor já atualizado. */
    public int incrementCounter(String vesselId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", AttributeValue.builder().s(Vessel.pkFor(vesselId)).build());
        key.put("SK", AttributeValue.builder().s(DefaultSeatUsageCounter.SK).build());

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":inc", AttributeValue.builder().n("1").build());
        values.put(":zero", AttributeValue.builder().n("0").build());

        var response = dynamoDbClient.updateItem(builder -> builder
                .tableName(tableName)
                .key(key)
                .updateExpression("SET vezesAplicado = if_not_exists(vezesAplicado, :zero) + :inc")
                .expressionAttributeValues(values)
                .returnValues(ReturnValue.UPDATED_NEW));

        return Integer.parseInt(response.attributes().get("vezesAplicado").n());
    }
}
