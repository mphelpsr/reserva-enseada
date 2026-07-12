package com.empresa.booking.infrastructure.dynamodb;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.empresa.booking.domain.booking.Buyer;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * BUYER#{id}/METADATA — ver Buyer.java. Só leitura: nenhum caso de uso deste
 * módulo escreve este item hoje (perfil "resolvida via Cognito", T003/T058)
 * — sem um fluxo de sincronização Cognito→DynamoDB (fora do escopo definido
 * em spec-booking.md), `findById` sempre retorna vazio na prática. Existe
 * para SesEmailNotifier já funcionar assim que esse fluxo existir, sem
 * precisar de mudança de contrato depois.
 */
@Repository
public class BuyerRepository {

    private final DynamoDbTable<Buyer> table;

    public BuyerRepository(DynamoDbEnhancedClient enhancedClient, @Value("${app.dynamodb.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Buyer.class));
    }

    public Optional<Buyer> findById(String buyerId) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(Buyer.pkFor(buyerId)).sortValue(Buyer.SK).build()));
    }
}
