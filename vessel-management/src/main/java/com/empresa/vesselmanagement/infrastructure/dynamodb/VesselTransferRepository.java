package com.empresa.vesselmanagement.infrastructure.dynamodb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.vesselmanagement.domain.cancellation.VesselTransfer;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/** Suporte a T040 (TransferVesselUseCase) e T040b (RemoveVesselUseCase). VESSEL#{id} / TRANSFER#{id}. */
@Repository
public class VesselTransferRepository {

    private final DynamoDbTable<VesselTransfer> table;

    public VesselTransferRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(VesselTransfer.class));
    }

    public void save(VesselTransfer transfer) {
        table.putItem(transfer);
    }
}
