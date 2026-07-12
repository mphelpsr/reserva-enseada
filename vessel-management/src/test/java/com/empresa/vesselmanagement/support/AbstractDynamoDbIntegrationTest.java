package com.empresa.vesselmanagement.support;

import java.net.URI;
import java.util.Map;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Base para os testes de integração da Fase 3.2 (T017-T022). Usa o padrão de
 * "singleton container" do Testcontainers (campo estático, sem @Testcontainers/
 * @Container) para compartilhar uma única instância de DynamoDB Local entre
 * todas as classes de teste do módulo, evitando o custo de subir um container
 * por classe. O Ryuk do Testcontainers derruba o container ao fim da JVM.
 *
 * O client aqui é só para SETUP/ASSERT direto dos testes (semear estado e
 * inspecionar o que ficou gravado) — não é o client de produção da aplicação,
 * que só passa a existir na Fase 3.3 (T032+). Por isso os testes que dependem
 * de lógica de negócio ainda não implementada falham hoje (endpoint 404 ou
 * comportamento ausente), como esperado pelo gate de TDD da Fase 3.2.
 */
public abstract class AbstractDynamoDbIntegrationTest {

    protected static final String TABLE_NAME = "VesselManagement-test";

    private static final GenericContainer<?> DYNAMODB_LOCAL;

    protected static final DynamoDbClient DYNAMO_DB_CLIENT;

    static {
        DYNAMODB_LOCAL = new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.5.2"))
                .withExposedPorts(8000);
        DYNAMODB_LOCAL.start();

        DYNAMO_DB_CLIENT = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + DYNAMODB_LOCAL.getHost() + ":" + DYNAMODB_LOCAL.getMappedPort(8000)))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();

        DYNAMO_DB_CLIENT.createTable(builder -> builder
                .tableName(TABLE_NAME)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .keySchema(
                        KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build())
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("PK").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("SK").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("GSI1PK").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("GSI1SK").attributeType(ScalarAttributeType.S).build())
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName("GSI1")
                        .keySchema(
                                KeySchemaElement.builder().attributeName("GSI1PK").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("GSI1SK").keyType(KeyType.RANGE).build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build()));
    }

    @DynamicPropertySource
    static void dynamoDbProperties(DynamicPropertyRegistry registry) {
        // Lidas pelo bean de DynamoDbClient da aplicação a partir da Fase 3.3 (T032).
        registry.add("app.dynamodb.endpoint-override",
                () -> "http://" + DYNAMODB_LOCAL.getHost() + ":" + DYNAMODB_LOCAL.getMappedPort(8000));
        registry.add("app.dynamodb.table-name", () -> TABLE_NAME);
    }

    /** Semeia um item diretamente na tabela, contornando a (ainda inexistente) camada de aplicação. */
    protected static void putItem(Map<String, AttributeValue> item) {
        DYNAMO_DB_CLIENT.putItem(builder -> builder.tableName(TABLE_NAME).item(item));
    }

    protected static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    protected static AttributeValue n(Number value) {
        return AttributeValue.builder().n(String.valueOf(value)).build();
    }
}
