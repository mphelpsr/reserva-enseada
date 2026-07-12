package com.empresa.booking.infrastructure.dynamodb;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.booking.domain.booking.VesselRecebedor;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/** VESSEL#{vesselId}/RECEBEDOR — ver VesselRecebedor.java. */
@Repository
public class VesselRecebedorRepository {

    private final DynamoDbTable<VesselRecebedor> table;

    public VesselRecebedorRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(VesselRecebedor.class));
    }

    public Optional<VesselRecebedor> findByVesselId(String vesselId) {
        return Optional.ofNullable(table.getItem(
                Key.builder().partitionValue("VESSEL#" + vesselId).sortValue(VesselRecebedor.SK).build()));
    }

    public void save(VesselRecebedor recebedor) {
        table.putItem(recebedor);
    }
}
