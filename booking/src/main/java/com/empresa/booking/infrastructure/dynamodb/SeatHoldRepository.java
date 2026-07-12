package com.empresa.booking.infrastructure.dynamodb;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.booking.domain.availability.TourType;
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

    /**
     * Holds expirados do vessel/data/tipoPasseio pedido, ainda não varridos
     * fisicamente pelo sweeper (T046, roda a cada 1 min). CreateHoldUseCase
     * consulta isto ANTES de tentar reservar — sem isto, um hold expirado
     * continuaria ocupando `vagasDisponiveis` até o próximo ciclo do sweeper,
     * violando a garantia de que hold expirado nunca bloqueia uma vaga nova
     * (CLAUDE.md, ressalva do hold de 10 minutos: "independente do item ainda
     * existir fisicamente").
     */
    public List<SeatHold> findExpiredByVesselDateType(String vesselId, LocalDate data, TourType tipoPasseio, Instant now) {
        Expression expiredForThisSlot = Expression.builder()
                .expression("SK = :sk AND begins_with(PK, :pkPrefix) AND vesselId = :vesselId AND #data = :data "
                        + "AND tipoPasseio = :tipoPasseio AND expiresAt < :now")
                .putExpressionName("#data", "data")
                .putExpressionValue(":sk", AttributeValue.builder().s(SeatHold.SK).build())
                .putExpressionValue(":pkPrefix", AttributeValue.builder().s("HOLD#").build())
                .putExpressionValue(":vesselId", AttributeValue.builder().s(vesselId).build())
                .putExpressionValue(":data", AttributeValue.builder().s(data.toString()).build())
                .putExpressionValue(":tipoPasseio", AttributeValue.builder().s(tipoPasseio.getValue()).build())
                .putExpressionValue(":now", AttributeValue.builder().s(now.toString()).build())
                .build();

        return table.scan(ScanEnhancedRequest.builder().filterExpression(expiredForThisSlot).build())
                .items()
                .stream()
                .toList();
    }
}
