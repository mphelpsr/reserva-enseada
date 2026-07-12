package com.empresa.booking.infrastructure.dynamodb;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.booking.Booking;
import com.empresa.booking.domain.booking.BookingStatus;
import com.empresa.booking.domain.booking.BookingVesselDateIndex;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * T033/T036/T037. BOOKING#{id}/METADATA + item ponteiro
 * VESSEL#{vesselId}/BOOKING#{data}#{id} (plan.md, access pattern 6) — as duas
 * escritas acontecem na MESMA transação para nunca divergir.
 */
@Repository
public class BookingRepository {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Booking> table;
    private final DynamoDbTable<BookingVesselDateIndex> vesselDateIndexTable;

    public BookingRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Booking.class));
        this.vesselDateIndexTable = enhancedClient.table(tableName, TableSchema.fromBean(BookingVesselDateIndex.class));
    }

    public Optional<Booking> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(Booking.pkFor(id)).sortValue(Booking.SK).build()));
    }

    /** FR-011: histórico do comprador, via GSI1 — sem scan. */
    public List<Booking> findByBuyerId(String buyerId) {
        return table.index("GSI1")
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue("BUYER#" + buyerId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    /**
     * FR-008/FR-009: reservas de um dia/embarcação afetadas por um evento do
     * vessel-management — filtra por `tipoPasseio` em memória, já que a SK do
     * item ponteiro só tem a data (ver BookingVesselDateIndex).
     */
    public List<Booking> findByVesselDateAndType(String vesselId, LocalDate data, TourType tipoPasseio) {
        return vesselDateIndexTable
                .query(QueryConditional.sortBeginsWith(Key.builder()
                        .partitionValue("VESSEL#" + vesselId)
                        .sortValue(BookingVesselDateIndex.skPrefixFor(data))
                        .build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(index -> index.getTipoPasseio() == tipoPasseio)
                .map(index -> findById(index.getBookingId()))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * T046 (sweeper): reservas AGUARDANDO_TRANSFERENCIA cuja janela de 48h
     * (FR-009) já expirou sem resposta do comprador — mesmo padrão de
     * SeatHoldRepository.findAll(), full scan aceitável dado o volume baixo
     * do módulo (Princípio VI).
     */
    public List<Booking> findAwaitingTransferExpiredBefore(Instant now) {
        Expression expiredTransferOffers = Expression.builder()
                .expression("SK = :sk AND begins_with(PK, :pkPrefix) AND #status = :status AND transferOfferExpiresAt < :now")
                .putExpressionName("#status", "status")
                .putExpressionValue(":sk", AttributeValue.builder().s(Booking.SK).build())
                .putExpressionValue(":pkPrefix", AttributeValue.builder().s("BOOKING#").build())
                .putExpressionValue(":status", AttributeValue.builder().s(BookingStatus.AGUARDANDO_TRANSFERENCIA.getValue()).build())
                .putExpressionValue(":now", AttributeValue.builder().s(now.toString()).build())
                .build();

        return table.scan(ScanEnhancedRequest.builder().filterExpression(expiredTransferOffers).build())
                .items()
                .stream()
                .toList();
    }

    /** Cria ou atualiza a reserva + seu item ponteiro na mesma transação. */
    public void save(Booking booking) {
        enhancedClient.transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(table, booking)
                .addPutItem(vesselDateIndexTable, BookingVesselDateIndex.builder()
                        .vesselId(booking.getVesselId())
                        .data(booking.getData())
                        .bookingId(booking.getId())
                        .tipoPasseio(booking.getTipoPasseio())
                        .build())
                .build());
    }

    /**
     * FR-009 (aceite de transferência): `booking.vesselId` já reflete a NOVA
     * embarcação — o item ponteiro antigo (`VESSEL#{vesselIdAnterior}/BOOKING#...`)
     * fica órfão se não for removido explicitamente, já que `save()` sempre
     * escreve o ponteiro sob o `vesselId` ATUAL do bean.
     */
    public void moveToVessel(Booking booking, String vesselIdAnterior) {
        enhancedClient.transactWriteItems(TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(table, booking)
                .addDeleteItem(vesselDateIndexTable, Key.builder()
                        .partitionValue("VESSEL#" + vesselIdAnterior)
                        .sortValue(BookingVesselDateIndex.skFor(booking.getData(), booking.getId()))
                        .build())
                .addPutItem(vesselDateIndexTable, BookingVesselDateIndex.builder()
                        .vesselId(booking.getVesselId())
                        .data(booking.getData())
                        .bookingId(booking.getId())
                        .tipoPasseio(booking.getTipoPasseio())
                        .build())
                .build());
    }
}
