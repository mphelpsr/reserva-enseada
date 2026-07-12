# vessel-management

Painel do proprietário — cadastro de embarcações, disponibilidade declarada, rodízio,
limite de vagas, alertas de maré/vento e a metade "vessel-management" da saga de
cancelamento/transferência com o módulo `booking`. Especificação completa em
[`specs/001-vessel-management/`](../specs/001-vessel-management/) (`spec.md`, `plan.md`,
`tasks.md`) e nos princípios de [`../.specify/memory/constitution.md`](../.specify/memory/constitution.md).

## Stack

- Java 21, Spring Boot 3.3.4
- AWS SDK v2 Enhanced Client — DynamoDB single-table (`PK`/`SK` + `GSI1`, schema em
  `infra/dynamodb.tf` e `plan.md` seção "Phase 1 — Data Model")
- SNS (publica) / SQS (consome `booking.*`) — nunca chamada HTTP síncrona com o módulo `booking`
  (Princípio VII, saga coreografada)
- AWS Lambda (SnapStart) via `spring-cloud-function-adapter-aws`, Terraform como IaC
- Stormglass (maré/vento) como alerta ao proprietário — nunca sobrescreve disponibilidade
  automaticamente (Princípio I)
- Testes: JUnit 5 + Mockito (unitários) e Testcontainers/DynamoDB Local (contrato e integração,
  T009-T022, incluindo teste de concorrência T022)

## Pré-requisitos

- JDK 21
- Maven 3.9+
- Docker (só para os testes de contrato/integração via Testcontainers — ver seção "Sandbox local
  sem Docker" se não tiver disponível)
- Terraform 1.7+ e credenciais AWS configuradas (só para deploy)
- Python 3 (usado internamente pelo `scripts/local-sandbox.sh` para criar a tabela)

## Build e testes

```bash
mvn clean compile          # compila
mvn test                   # só testes unitários (application/, domain/) — não precisam de Docker
mvn verify                 # tudo, incluindo contrato/integração (T009-T022) — precisa de Docker
mvn package -DskipTests    # gera target/vessel-management.jar (execução local) e
                            # target/vessel-management-aws.jar (uber-jar p/ Lambda, classifier "aws")
```

O CI (`.github/workflows/vessel-management-ci.yml`) roda `mvn verify` em todo push/PR que toque
este diretório — os runners do GitHub Actions já vêm com Docker, então os testes de contrato e
integração rodam de verdade lá mesmo que você não consiga rodá-los localmente.

## Sandbox local sem Docker

Este ambiente de desenvolvimento não tem Docker funcional (o cliente `docker-java` usado pelo
Testcontainers está preso numa API antiga demais para o daemon disponível). Para conseguir
validar a aplicação de ponta a ponta mesmo assim, `scripts/local-sandbox.sh` automatiza um
sandbox 100% sem Docker: baixa o **DynamoDB Local standalone** (jar oficial da AWS, não o
container), sobe em memória, cria a tabela com o mesmo schema do Terraform, builda o jar e roda
a aplicação Spring Boot real contra ele.

Se você tiver Docker disponível na sua máquina, prefira `mvn verify` (Testcontainers) para os
testes automatizados — o sandbox é para exploração manual (curl, Swagger) e para ambientes sem
Docker.

```bash
./scripts/local-sandbox.sh start    # baixa (1a vez), cria a tabela, builda e sobe a app
./scripts/local-sandbox.sh status   # mostra o que está rodando (PIDs)
./scripts/local-sandbox.sh stop     # derruba app + DynamoDB Local
```

Ao final do `start`, o script imprime um exemplo de `curl` para cadastrar uma embarcação e os
endereços do Swagger/OpenAPI. Por padrão:

| O quê             | Endereço                              |
|-------------------|----------------------------------------|
| DynamoDB Local    | http://localhost:8000                  |
| Aplicação         | http://localhost:8080                  |
| OpenAPI (JSON)    | http://localhost:8080/v3/api-docs      |
| Swagger UI        | http://localhost:8080/swagger-ui.html  |
| Logs              | `/tmp/vessel-management-sandbox/*.log` |

Portas e diretório de trabalho são configuráveis por variável de ambiente antes de chamar o
script:

| Variável              | Padrão                              | Efeito                          |
|-----------------------|--------------------------------------|----------------------------------|
| `DDB_PORT`             | `8000`                              | Porta do DynamoDB Local          |
| `APP_PORT`             | `8080`                              | Porta da aplicação                |
| `SANDBOX_DIR`          | `/tmp/vessel-management-sandbox`    | Onde ficam jar baixado, PIDs, logs |
| `DYNAMODB_TABLE_NAME`  | `reserva-enseada-vessel-management-dev` | Nome da tabela criada localmente |

O script é idempotente: chamar `start` de novo com a app/DynamoDB já rodando não duplica
processos, e `create_table` pula a criação se a tabela já existir. Os PIDs ficam em
`$SANDBOX_DIR/*.pid` para o `stop`/`status` conseguirem rastrear os processos entre chamadas.

### Exemplo de uso manual

```bash
curl -X POST http://localhost:8080/vessels \
  -H "Content-Type: application/json" \
  -d '{"ownerId":"owner-1","nomeLegal":"Sereia do Mar","nomeFantasia":"Passeios Sereia","numeroRegistroCapitania":"CP-1","cpfCnpjProprietario":"111","capacidadeMaxima":20,"portoSaida":"Porto A"}'
```

Explore os demais endpoints (disponibilidade, rodízio, limite de vagas, calendário, advisory) via
Swagger UI — todos os `@RestController` da Fase 3.4 são introspectados automaticamente pelo
springdoc (T061), sem anotação extra.

## Deploy (Terraform)

```bash
# 1. Uma única vez, com state local: cria o bucket S3 + tabela DynamoDB de lock do backend remoto
cd infra/bootstrap && terraform init && terraform apply

# 2. Empacotar o uber-jar que os Lambdas vão usar
cd ../.. && mvn package -DskipTests   # gera target/vessel-management-aws.jar

# 3. Aplicar a infraestrutura de fato (agora já usando o backend S3 do passo 1)
cd infra && terraform init && terraform plan && terraform apply
```

Variáveis relevantes (`infra/variables.tf`): `aws_region`, `environment`, `project_name`,
`owner_panel_callback_urls`, `lambda_artifact_path` (default aponta para o jar do passo 2) e
`stormglass_api_key` (sensível — vazio faz o advisory job pular o cálculo sem erro, Princípio I).

Cada um dos 3 Lambdas (`api`, `advisory_job`, `booking_events_consumer`) tem sua própria IAM role
com permissões mínimas (T062) — ver `infra/lambda.tf`.

## Limitações conhecidas

- **Ponte HTTP do Lambda não validada em ambiente real**: sem `spring-cloud-function-web` (removido
  deliberadamente — ver comentário em `pom.xml`), o `FunctionInvoker` não tem uma ponte documentada
  até o `DispatcherServlet` do Spring MVC. Os controllers foram validados via Tomcat embarcado
  (sandbox local) e MockMvc, não via Lambda real. Antes do primeiro deploy AWS, avaliar
  `aws-serverless-java-container-springboot3` como adaptador.
- **Stormglass sem coordenadas obrigatórias**: `Vessel.latitude`/`longitude` são opcionais (FR-001
  não captura coordenadas) — sem elas, o advisory job pula o cálculo daquela embarcação.
- **T059 (consumir `booking.transferred`/`booking.cancelled`) depende do módulo `booking`**: o
  payload do evento precisa ser combinado com a implementação do lado `booking` (T055 de
  `tasks-booking.md`) antes de fechar essa tarefa — ver nota em `specs/001-vessel-management/tasks.md`.
- **`ConfirmedBookingCount` é uma réplica local via evento `booking.confirmed`**: não há
  consulta síncrona ao módulo `booking` (Princípio VII) — o contador reflete o último evento
  consumido, não uma fonte de verdade transacional entre módulos.
