package com.empresa.vesselmanagement.infrastructure.dynamodb;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.vesselmanagement.domain.vessel.Vessel;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactPutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

@Repository
public class VesselRepository {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Vessel> table;
    private final DynamoDbTable<VesselIdentifierGuard> guardTable;

    public VesselRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Vessel.class));
        this.guardTable = enhancedClient.table(tableName, TableSchema.fromBean(VesselIdentifierGuard.class));
    }

    public Optional<Vessel> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(Vessel.pkFor(id)).sortValue(Vessel.SK).build()));
    }

    public List<Vessel> findByOwnerId(String ownerId) {
        return table.index("GSI1")
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue("OWNER#" + ownerId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    /** FR-001, FR-009: rejeita cadastro com identificador único já existente. */
    public void create(Vessel vessel) {
        VesselIdentifierGuard guard = VesselIdentifierGuard.builder()
                .uniqueKey(vessel.uniqueIdentifierKey())
                .vesselId(vessel.getId())
                .build();

        try {
            enhancedClient.transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                    .addPutItem(table, TransactPutItemEnhancedRequest.builder(Vessel.class)
                            .item(vessel)
                            .conditionExpression(attributeNotExists())
                            .build())
                    .addPutItem(guardTable, TransactPutItemEnhancedRequest.builder(VesselIdentifierGuard.class)
                            .item(guard)
                            .conditionExpression(attributeNotExists())
                            .build())
                    .build());
        } catch (TransactionCanceledException e) {
            throw new DuplicateVesselIdentifierException(
                    "Já existe uma embarcação com este nº de registro + CPF/CNPJ + nome legal (FR-009)");
        }
    }

    public void update(Vessel vessel) {
        table.putItem(vessel);
    }

    public void delete(String id) {
        table.deleteItem(Key.builder().partitionValue(Vessel.pkFor(id)).sortValue(Vessel.SK).build());
    }

    private static software.amazon.awssdk.enhanced.dynamodb.Expression attributeNotExists() {
        return software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                .expression("attribute_not_exists(PK)")
                .build();
    }
}
