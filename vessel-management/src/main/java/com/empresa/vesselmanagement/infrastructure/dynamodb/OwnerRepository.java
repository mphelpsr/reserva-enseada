package com.empresa.vesselmanagement.infrastructure.dynamodb;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.vesselmanagement.domain.vessel.Owner;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class OwnerRepository {

    private final DynamoDbTable<Owner> table;

    public OwnerRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Owner.class));
    }

    public Optional<Owner> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(Owner.pkFor(id)).sortValue(Owner.SK).build()));
    }

    public void save(Owner owner) {
        table.putItem(owner);
    }
}
