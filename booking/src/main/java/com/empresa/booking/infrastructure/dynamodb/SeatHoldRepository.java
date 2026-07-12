package com.empresa.booking.infrastructure.dynamodb;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.booking.domain.seathold.SeatHold;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** T034. HOLD#{id}/METADATA — retenção temporária de vagas (FR-004). */
@Repository
public class SeatHoldRepository {

    private final DynamoDbTable<SeatHold> table;

    public SeatHoldRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(SeatHold.class));
    }

    public DynamoDbTable<SeatHold> table() {
        return table;
    }

    public Optional<SeatHold> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(SeatHold.pkFor(id)).sortValue(SeatHold.SK).build()));
    }

    public void save(SeatHold hold) {
        table.putItem(hold);
    }

    public void delete(String id) {
        table.deleteItem(Key.builder().partitionValue(SeatHold.pkFor(id)).sortValue(SeatHold.SK).build());
    }

    /**
     * T046 (sweeper): todos os holds da tabela. Full scan com filtro (SK=METADATA
     * e PK começa com HOLD#) — aceitável dado o volume baixo do módulo
     * (Princípio VI), mesmo padrão de vessel-management VesselRepository.findAll().
     */
    public List<SeatHold> findAll() {
        Expression onlyHoldMetadata = Expression.builder()
                .expression("SK = :sk AND begins_with(PK, :pkPrefix)")
                .putExpressionValue(":sk", AttributeValue.builder().s(SeatHold.SK).build())
                .putExpressionValue(":pkPrefix", AttributeValue.builder().s("HOLD#").build())
                .build();

        return table.scan(ScanEnhancedRequest.builder().filterExpression(onlyHoldMetadata).build())
                .items()
                .stream()
                .toList();
    }
}
