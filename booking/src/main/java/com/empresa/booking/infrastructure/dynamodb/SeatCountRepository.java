package com.empresa.booking.infrastructure.dynamodb;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.booking.domain.availability.TourType;
import com.empresa.booking.domain.seathold.SeatCount;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * T035. VESSEL#{vesselId}/SEATCOUNT#{data}#{tipoPasseio} — núcleo técnico do
 * FR-003 (overselling). `held`/`sold` são alterados via `UpdateItem` cru com
 * `ConditionExpression` (mesmo padrão de vessel-management SeatLimitRepository/
 * BookingCountRepository) — atômico por natureza de item único, sem precisar
 * de `TransactWriteItems` cross-item: o SDK garante que a condição
 * `limite - sold - held >= :qty` é avaliada e aplicada atomicamente pelo
 * DynamoDB, então duas requisições concorrentes pela última vaga nunca
 * conseguem incrementar `held` além da capacidade — é essa garantia que
 * evita overselling, não uma transação entre itens.
 */
@Repository
public class SeatCountRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final DynamoDbTable<SeatCount> table;

    public SeatCountRepository(
            DynamoDbEnhancedClient enhancedClient, DynamoDbClient dynamoDbClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(SeatCount.class));
    }

    public Optional<SeatCount> findByVesselDateType(String vesselId, LocalDate data, TourType tipoPasseio) {
        return Optional.ofNullable(table.getItem(Key.builder()
                .partitionValue("VESSEL#" + vesselId)
                .sortValue(SeatCount.skFor(data, tipoPasseio))
                .build()));
    }

    /**
     * FR-001/FR-002: sinal de "esta embarcação existe" para GetVesselCalendarReadModelUseCase —
     * este módulo nunca recebe um evento dedicado a "embarcação registrada", só réplica
     * disponibilidade/limite; então "nunca recebemos NENHUM SeatCount para este vesselId"
     * é o único proxy disponível para distinguir embarcação inexistente de embarcação
     * existente sem dado ainda para o intervalo pedido.
     */
    public boolean existsForVessel(String vesselId) {
        QueryConditional condition = QueryConditional.sortBeginsWith(Key.builder()
                .partitionValue("VESSEL#" + vesselId)
                .sortValue("SEATCOUNT#")
                .build());
        return table.query(QueryEnhancedRequest.builder().queryConditional(condition).limit(1).build())
                .stream()
                .flatMap(page -> page.items().stream())
                .findAny()
                .isPresent();
    }

    /** FR-003: incrementa `held` só se sobrar capacidade — false = vagas insuficientes. */
    public boolean tryIncrementHeld(String vesselId, LocalDate data, TourType tipoPasseio, int quantidade) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":qty", n(quantidade));
        try {
            dynamoDbClient.updateItem(builder -> builder
                    .tableName(tableName)
                    .key(key(vesselId, data, tipoPasseio))
                    .updateExpression("SET held = held + :qty")
                    .conditionExpression("attribute_exists(PK) AND (limite - sold - held) >= :qty")
                    .expressionAttributeValues(values));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    /** Compensação best-effort se a criação do HOLD falhar após incrementar `held`. */
    public void decrementHeld(String vesselId, LocalDate data, TourType tipoPasseio, int quantidade) {
        addToCounter("held", vesselId, data, tipoPasseio, -quantidade);
    }

    /** FR-005: confirmação de pagamento move `held` → `sold` definitivamente. */
    public void moveHeldToSold(String vesselId, LocalDate data, TourType tipoPasseio, int quantidade) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":qty", n(quantidade));
        dynamoDbClient.updateItem(builder -> builder
                .tableName(tableName)
                .key(key(vesselId, data, tipoPasseio))
                .updateExpression("SET held = held - :qty, sold = sold + :qty")
                .expressionAttributeValues(values));
    }

    /** FR-006/FR-007/FR-008: cancelamento de reserva confirmada libera a vaga vendida. */
    public void decrementSold(String vesselId, LocalDate data, TourType tipoPasseio, int quantidade) {
        addToCounter("sold", vesselId, data, tipoPasseio, -quantidade);
    }

    /** FR-009 (aceite de transferência): a embarcação de destino ganha a venda que a origem perdeu. */
    public void incrementSold(String vesselId, LocalDate data, TourType tipoPasseio, int quantidade) {
        addToCounter("sold", vesselId, data, tipoPasseio, quantidade);
    }

    /** FR-013, Opção C: sempre aceita, mesmo abaixo do já vendido/retido — sem bloqueio. */
    public void upsertLimite(String vesselId, LocalDate data, TourType tipoPasseio, int limite) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":limite", n(limite));
        values.put(":zero", n(0));
        dynamoDbClient.updateItem(builder -> builder
                .tableName(tableName)
                .key(key(vesselId, data, tipoPasseio))
                .updateExpression("SET limite = :limite, sold = if_not_exists(sold, :zero), held = if_not_exists(held, :zero)")
                .expressionAttributeValues(values));
    }

    /** Réplica local de `vessel.availability.changed` — mesmo item, ver SeatCount. */
    public void upsertDisponibilidade(String vesselId, LocalDate data, TourType tipoPasseio, boolean disponivel, String motivo) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":disponivel", AttributeValue.builder().bool(disponivel).build());
        values.put(":motivo", motivo == null ? AttributeValue.builder().nul(true).build() : AttributeValue.builder().s(motivo).build());
        values.put(":zero", n(0));
        dynamoDbClient.updateItem(builder -> builder
                .tableName(tableName)
                .key(key(vesselId, data, tipoPasseio))
                .updateExpression(
                        "SET disponivel = :disponivel, motivo = :motivo, "
                                + "sold = if_not_exists(sold, :zero), held = if_not_exists(held, :zero), limite = if_not_exists(limite, :zero)")
                .expressionAttributeValues(values));
    }

    private void addToCounter(String attribute, String vesselId, LocalDate data, TourType tipoPasseio, int delta) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":delta", n(delta));
        values.put(":zero", n(0));
        try {
            dynamoDbClient.updateItem(builder -> builder
                    .tableName(tableName)
                    .key(key(vesselId, data, tipoPasseio))
                    .updateExpression("SET " + attribute + " = if_not_exists(" + attribute + ", :zero) + :delta")
                    .conditionExpression("attribute_exists(PK)")
                    .expressionAttributeValues(values));
        } catch (ConditionalCheckFailedException ignored) {
            // item nunca existiu (ex.: hold de um dia que nunca teve limite definido) — nada a decrementar
        }
    }

    private Map<String, AttributeValue> key(String vesselId, LocalDate data, TourType tipoPasseio) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", AttributeValue.builder().s("VESSEL#" + vesselId).build());
        key.put("SK", AttributeValue.builder().s(SeatCount.skFor(data, tipoPasseio)).build());
        return key;
    }

    private static AttributeValue n(int value) {
        return AttributeValue.builder().n(String.valueOf(value)).build();
    }
}
