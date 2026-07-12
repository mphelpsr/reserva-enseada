# booking

App do comprador — consulta de disponibilidade, reserva (hold + confirmação), pagamento com
split via Pagar.me, cancelamento/transferência e a metade "booking" da saga coreografada de
cancelamento com o módulo `vessel-management`. Especificação completa em
[`specs/002-booking/`](../specs/002-booking/) (`spec.md`, `plan.md`, `tasks.md`) e nos princípios
de [`../.specify/memory/constitution.md`](../.specify/memory/constitution.md).

## Stack

- Java 21, Spring Boot 3.3.4
- AWS SDK v2 Enhanced Client — DynamoDB single-table (`PK`/`SK` + `GSI1`, schema em
  `infra/dynamodb.tf` e `plan.md` seção "Phase 1 — Data Model")
- SNS (publica `booking.confirmed`/`booking.cancelled`/`booking.transferred`) / SQS (consome os 4
  tópicos do `vessel-management`) — nunca chamada HTTP síncrona entre os dois módulos (Princípio
  VII, saga coreografada)
- AWS Lambda via `spring-cloud-function-adapter-aws` (3 funções: `api`, `operator_events_consumer`,
  `sweeper`), Terraform como IaC — cold start mitigado por *provisioned concurrency* opcional no
  endpoint de hold/checkout (T010, off por padrão), não SnapStart (ver `plan.md`, "Justificativa
  da escolha de compute")
- Pagar.me (split de pagamento, FR-015) via `PagarmeClient` (`infra/external`) — comissão da
  plataforma configurável (`PLATFORM_COMMISSION_PERCENTAGE`), nunca hardcoded
- SES (e-mail transacional, FR-010) e Cognito (autorização do comprador no API Gateway)
- Testes: JUnit 5 + Mockito (unitários) e Testcontainers/DynamoDB Local + WireMock (contrato e
  integração, T012-T026, incluindo teste de concorrência T018)

## Pré-requisitos

- JDK 21
- Maven 3.9+
- Docker (para os testes de contrato/integração via Testcontainers — DynamoDB Local roda em
  container; WireMock stuba o Pagar.me, sem chamada de rede real)
- Terraform 1.7+ e credenciais AWS configuradas (só para deploy)

## Build e testes

```bash
mvn clean compile          # compila
mvn test                   # só testes unitários (application/, domain/) — não precisam de Docker
mvn verify                 # tudo, incluindo contrato/integração (T012-T026) — precisa de Docker
mvn package -DskipTests    # gera target/booking.jar (execução local) e
                            # target/booking-aws.jar (uber-jar p/ Lambda, classifier "aws")
```

O CI (`.github/workflows/booking-ci.yml`) roda `mvn verify` em todo push/PR que toque este
diretório — os runners do GitHub Actions já vêm com Docker, então os testes de contrato e
integração (incluindo o teste de concorrência T018 sobre `SeatCount`) rodam de verdade lá mesmo
que você não consiga rodá-los localmente.

## Rodando localmente (Testcontainers)

Não há um script de sandbox dedicado neste módulo (diferente do `vessel-management`) — a forma
suportada de rodar a aplicação de ponta a ponta é `mvn verify`, que sobe DynamoDB Local via
Testcontainers e um servidor WireMock local simulando a API do Pagar.me para cada classe de teste
de contrato/integração (`support/AbstractDynamoDbIntegrationTest`, ver também
`T013_ConfirmBookingContractTest`/`T026_PaymentSplitIntegrationTest` para o padrão de stub). Não é
necessário nem recomendado apontar `pagarme-base-url` para o ambiente real do Pagar.me durante o
desenvolvimento local.

Para explorar a API manualmente contra dados reais, use a sandbox pública do Pagar.me
(`PAGARME_BASE_URL` já aponta para produção por padrão — configure uma chave de sandbox do
Pagar.me em `PAGARME_API_KEY` e rode a aplicação localmente com `mvn spring-boot:run` contra um
DynamoDB Local iniciado manualmente, já que não há automação de subida local além dos testes).

Os `@RestController` (`BookingController`, `CalendarController`) são introspectados
automaticamente pelo springdoc (T060, sem anotação extra) em `/v3/api-docs` e `/swagger-ui.html`
quando a aplicação está de pé.

## Deploy (Terraform)

```bash
# 1. Uma única vez, com state local: cria o bucket S3 + tabela DynamoDB de lock do backend remoto
cd infra/bootstrap && terraform init && terraform apply

# 2. Empacotar o uber-jar que os Lambdas vão usar
cd ../.. && mvn package -DskipTests   # gera target/booking-aws.jar

# 3. Aplicar a infraestrutura de fato (agora já usando o backend S3 do passo 1)
cd infra && terraform init && terraform plan && terraform apply
```

Variáveis relevantes (`infra/variables.tf`): `aws_region`, `environment`, `project_name`,
`buyer_app_callback_urls`, `lambda_artifact_path` (default aponta para o jar do passo 2),
`pagarme_api_key` (sensível), `platform_commission_percentage` (default 12%, ver FR-015),
`ses_domain`/`ses_source_email` (vazios pulam a verificação de domínio/o envio de e-mail sem
erro, mesma resiliência do `stormglass_api_key` no `vessel-management`) e
`enable_provisioned_concurrency`/`provisioned_concurrency_count` (T010, ligar manualmente em
janelas de alta temporada conhecidas).

Cada um dos 3 Lambdas (`api`, `operator_events_consumer`, `sweeper`) tem sua própria IAM role com
permissões mínimas (T061) — ver `infra/lambda.tf`. A permissão de SES (`ses:SendEmail`/
`ses:SendTemplatedEmail`) fica restrita à identidade de domínio verificada
(`aws_ses_domain_identity.transactional`) quando `var.ses_domain` está configurado; só cai para
`Resource = "*"` no modo dev/local (`ses_domain` vazio), quando essa identidade não existe.

## Limitações conhecidas

- **Sem script de sandbox local automatizado**: diferente do `vessel-management`
  (`scripts/local-sandbox.sh`), rodar a aplicação de ponta a ponta localmente sem Docker não é
  suportado hoje — a validação de comportamento real depende de `mvn verify`
  (Testcontainers/WireMock) ou do CI.
- **Ponte HTTP do Lambda não validada em ambiente real**: mesma decisão e ressalva do
  `vessel-management` — sem `spring-cloud-function-web`, o `FunctionInvoker` não tem uma ponte
  documentada até o `DispatcherServlet` do Spring MVC. Os controllers foram validados via Tomcat
  embarcado (MockMvc/contract tests), não via Lambda real. Antes do primeiro deploy AWS, avaliar
  `aws-serverless-java-container-springboot3` como adaptador.
- **`BuyerRepository` é somente leitura e hoje nunca é populado**: não há cadastro de compradores
  neste módulo ainda — ver comentário na própria classe.
