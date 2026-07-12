package com.empresa.vesselmanagement.infrastructure.dynamodb;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.vesselmanagement.domain.cancellation.BookingTransferAttempt;
import com.empresa.vesselmanagement.domain.vessel.Vessel;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
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

    /**
     * T059: correlação por `transferAttemptId` (GetItem direto), nunca por
     * `(vesselId, data, tipoPasseio)` — ver plan.md, "Contrato da Saga", nota
     * sobre por que essa chave composta não é única o suficiente ao longo do
     * tempo.
     */
    public Optional<BookingTransferAttempt> findByVesselIdAndId(String vesselId, String id) {
        return Optional.ofNullable(
                table.getItem(Key.builder().partitionValue(Vessel.pkFor(vesselId)).sortValue(BookingTransferAttempt.skFor(id)).build()));
    }
}
