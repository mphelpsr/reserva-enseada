package com.empresa.vesselmanagement.infrastructure.dynamodb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.vesselmanagement.domain.cancellation.BookingTransferAttempt;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/** Suporte a T046 (CancelDayWithBookingsUseCase) e T059 (fecha a Saga). VESSEL#{id} / TRANSFER#{id}. */
@Repository
public class BookingTransferAttemptRepository {

    private final DynamoDbTable<BookingTransferAttempt> table;

    public BookingTransferAttemptRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(BookingTransferAttempt.class));
    }

    public void save(BookingTransferAttempt attempt) {
        table.putItem(attempt);
    }
}
