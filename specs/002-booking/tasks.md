# Tasks — Módulo de Booking (Comprador de Tickets)

**Baseado em:** `plan-booking.md` + `spec-booking.md`
**Convenção:** `[P]` = pode ser feita em paralelo com outras tarefas `[P]` (arquivos/módulos diferentes, sem dependência entre si). Tarefas sem `[P]` têm dependência sequencial explícita.

---

## Fase 3.1 — Setup (infraestrutura base)

- **T001** Configurar backend de estado do Terraform (workspace próprio do booking) em `infra/backend.tf`
- **T002** Terraform: tabela DynamoDB `Booking` com PK/SK conforme `plan-booking.md` (Data Model) + GSI1 (`GSI1PK=BUYER#id`, `GSI1SK=BOOKING#id`)
- **T003** Terraform: Cognito User Pool do comprador (isolado do pool do proprietário — decisão confirmada)
- **T004** Terraform: API Gateway HTTP API + autorizador Cognito
- **T005** Terraform: filas SQS assinando os tópicos SNS do vessel-management (`vessel.availability.changed`, `vessel.seatlimit.changed`, `vessel.cancellation.operator-initiated`, `vessel.transfer.viable`)
- **T006** Terraform: tópicos SNS publicados pelo booking (`booking.confirmed`, `booking.cancelled`, `booking.transferred`)
- **T007** Terraform: EventBridge Scheduler (a cada 1 min) + Lambda para o sweeper de holds expirados
- **T008** Terraform: verificação de domínio SES + templates de e-mail (FR-010)
- **T009** Scaffold do projeto Java (Spring Boot 3.x, Gradle/Maven), dependências: AWS SDK v2 Enhanced Client, cliente HTTP para Pagar.me
- **T010** Configurar *provisioned concurrency* nos endpoints de hold/checkout (ativação manual sob demanda em janelas de alta temporada — não bloqueia deploy inicial)

**Checkpoint:** infraestrutura provisionável via `terraform apply`, filas SQS já recebendo mensagens de teste dos tópicos do vessel-management.

---

## Fase 3.2 — Testes primeiro (TDD — devem falhar antes da Fase 3.3)

- **T011 [P]** Contract test: `GET /vessels/{id}/calendar` (read-model replicado do vessel-management, FR-001/FR-002)
- **T012 [P]** Contract test: `POST /bookings/hold` (FR-003, FR-004)
- **T013 [P]** Contract test: `POST /bookings/{holdId}/confirm` (FR-005)
- **T014 [P]** Contract test: `POST /bookings/{id}/cancel` (FR-006/FR-007, modelo binário)
- **T015 [P]** Contract test: `POST /bookings/{id}/respond-transfer` (FR-009)
- **T016 [P]** Contract test: `GET /bookings` (FR-011)
- **T017 [P]** Contract test: `GET /bookings/{id}`
- **T018 [P]** Teste de concorrência: dois holds simultâneos disputando a última vaga — apenas um sucede, sem overselling (FR-003, cenário 3)
- **T019 [P]** Integration test: hold expirado é ignorado no cálculo de vagas mesmo antes do TTL físico do DynamoDB apagar o item; sweeper reconcilia o contador em até 1 min (ponto técnico do plan)
- **T020 [P]** Integration test: compra recusada com menos de 24h antes da saída (FR-014, cenário 9)
- **T021 [P]** Integration test: cancelamento dentro da janela (7 dias/48h) = reembolso integral automático; fora da janela = recusado, sem escalonamento (FR-006/FR-007, cenários 4/5)
- **T022 [P]** Integration test: consumo de `vessel.cancellation.operator-initiated` dispara reembolso integral automático com motivo real comunicado (FR-008, cenário 6)
- **T023 [P]** Integration test: consumo de `vessel.transfer.viable` notifica o comprador e aguarda até 48h; sem resposta, cancela com reembolso integral (FR-009, cenário 7)
- **T024 [P]** Integration test: corrida entre cancelamento do comprador e evento de transferência — cancelamento do comprador sempre prevalece (FR-009, regra de prioridade)
- **T025 [P]** Integration test: redução do `PlatformSeatLimit` (evento `vessel.seatlimit.changed`) nunca deixa vagas restantes negativas nem afeta reservas já confirmadas (FR-013, Opção C)
- **T026 [P]** Integration test: transação de confirmação aplica split de 12% para a plataforma e 88% para o proprietário no Pagar.me (FR-015)

**Checkpoint:** todos os testes acima devem existir e falhar antes de iniciar a Fase 3.3.

---

## Fase 3.3 — Core (domínio, repositórios, casos de uso — só após Fase 3.2 falhar)

### Domínio (entidades)
- **T027 [P]** `domain/booking/Booking.java` (inclui valor pago, comissão, valor líquido — FR-015)
- **T028 [P]** `domain/booking/Buyer.java`
- **T029 [P]** `domain/seathold/SeatHold.java` (com atributo `expiresAt`)
- **T030 [P]** `domain/seathold/SeatCount.java` (réplica local: `limite`, `sold`, `held`)
- **T031 [P]** `domain/cancellation/CancellationPolicy.java` (modelo binário FR-006/FR-007)
- **T032 [P]** `domain/operatorevents/{VesselAvailabilityChanged, VesselSeatLimitChanged, OperatorInitiatedCancellation, VesselTransferViable}.java` (modelos dos eventos consumidos do vessel-management)

### Repositórios (infra/dynamodb — dependem de T002 e T027-T032)
- **T033** `BookingRepository`
- **T034** `SeatHoldRepository`
- **T035** `SeatCountRepository` (escrita condicional atômica — núcleo técnico do FR-003)
- **T036** Query GSI1 para listar reservas por comprador (FR-011)
- **T037** Query `VESSEL#id / BOOKING#data` para localizar reservas afetadas por um evento do vessel-management (FR-008, FR-009)

### Casos de uso (application/ — dependem da camada de repositórios)
- **T038** `CreateHoldUseCase` (FR-003, FR-004 — `TransactWriteItems`: cria hold + incrementa `held` condicionalmente)
- **T039** `ConfirmBookingUseCase` (FR-005 — confirma pagamento no Pagar.me com split de 12%, `TransactWriteItems` movendo `held→sold`, publica `booking.confirmed`)
- **T040** `CancelBookingByBuyerUseCase` (FR-006/FR-007 — modelo binário, sem escalonamento)
- **T041** `RespondToTransferUseCase` (FR-009 — aplica a regra de prioridade do comprador quando há corrida com cancelamento, T024)
- **T042** `ProcessOperatorCancellationUseCase` (consumidor do evento `vessel.cancellation.operator-initiated`, FR-008 — reembolso automático + motivo real)
- **T043** `ProcessTransferOfferUseCase` (consumidor do evento `vessel.transfer.viable`, FR-009 — notifica comprador, agenda expiração de 48h)
- **T044** `ListBuyerBookingsUseCase` (FR-011)
- **T045** `GetVesselCalendarReadModelUseCase` (lê a réplica local de disponibilidade, atualizada via evento)
- **T046** `ReleaseExpiredHoldsJob` (sweeper — decrementa `held` de holds vencidos, roda a cada 1 min via EventBridge Scheduler)

**Checkpoint:** testes unitários da Fase 3.2 relativos a domínio/casos de uso devem passar.

---

## Fase 3.4 — Integração (API, mensageria, pagamento, notificação, auth)

- **T047** `BookingController` — `POST /bookings/hold`, `POST /bookings/{holdId}/confirm`, `POST /bookings/{id}/cancel`, `POST /bookings/{id}/respond-transfer`, `GET /bookings`, `GET /bookings/{id}`
- **T048** `CalendarController` — `GET /vessels/{id}/calendar`
- **T049** SQS Listener: `vessel.availability.changed` → atualiza read-model consumido por T045
- **T050** SQS Listener: `vessel.seatlimit.changed` → atualiza `SeatCount.limite` (Opção C, FR-013)
- **T051** SQS Listener: `vessel.cancellation.operator-initiated` → aciona T042
- **T052** SQS Listener: `vessel.transfer.viable` → aciona T043
- **T052b** SQS Listener: `vessel.recebedor.changed` — **nova tarefa, criada em 2026-07-12** (ver "Contrato da Saga" em `plan-booking.md`). **Implementado na Fase 3.4**: `OperatorEventsConsumer.handleRecebedorChanged` mantém a réplica local `VESSEL#<vesselId>/RECEBEDOR` (`VesselRecebedor`/`VesselRecebedorRepository`) usada por `ConfirmBookingUseCase` (T039) para montar o split no Pagar.me. **Nota de 2026-07-12** (mesma data em que T059c foi adiada do lado vessel-management): o publisher que alimentaria este listener não vai ser implementado no formato original (`payment_recebedor_id` por proprietário) — ver nota em `spec-vessel-management.md` (FR-016) e `plan-vessel-management.md` sobre o modelo de conta única em desenho. Este listener fica pronto e inofensivo esperando um evento que não deve chegar nesse formato; revisar junto quando o novo modelo de repasse for desenhado (pode deixar de fazer sentido tal como está, dependendo do desenho escolhido).
- **T053** SNS Publisher: `booking.confirmed` (disparado por T039). **Payload confirmado em 2026-07-12** (ver "Contrato da Saga" em `plan-booking.md`): `{vesselId, data: "yyyy-MM-dd", tipoPasseio: "alto_mar"|"orla"}` — o vessel-management já consome exatamente este formato (T059b, implementado). Publicar esses 3 campos, nada mais.
- **T054** SNS Publisher: `booking.cancelled` (disparado por T040 ou T042). **Payload confirmado em 2026-07-12**: `{vesselId, data, tipoPasseio, bookingId, transferAttemptId: string|null}`. `transferAttemptId` = o `id` recebido em `vessel.transfer.viable` (persistido por T043 em `VesselTransferViable`/estado local da oferta) quando este cancelamento é a resolução (recusa ou expiração das 48h) de uma oferta de transferência pendente; em desistência direta do comprador via T040 (FR-006, sem transferência envolvida), enviar `null`.
- **T055** SNS Publisher: `booking.transferred` (disparado por T041) — **esta tarefa fecha a pendência T059 deixada em `tasks-vessel-management.md`**: o vessel-management precisa assinar este mesmo tópico (junto com `booking.cancelled`) para efetivar o passo final da Saga de cancelamento. **Payload confirmado em 2026-07-12**: `{vesselId, data, tipoPasseio, bookingId, targetVesselId, transferAttemptId}` — `vesselId`/`data`/`tipoPasseio` são os da embarcação ORIGINAL (a que recebeu `vessel.transfer.viable`), não os do destino; `transferAttemptId` nunca é `null` aqui (todo `booking.transferred` é resposta a uma oferta). Ver "Contrato da Saga" em `plan-booking.md`/`plan-vessel-management.md` para o racional completo — não é mais preciso combinar o payload do zero ao implementar, só seguir o que já está fechado.
- **T056** `PagarmeClient` (infra/payment) — criação de transação com split (FR-015), estorno (FR-006/FR-007/FR-008)
- **T057** Integração SES — templates de e-mail de confirmação/cancelamento/transferência (FR-010)
- **T058** Autorizador Cognito conectado ao API Gateway (T003/T004), validando token do comprador em todas as rotas

**Checkpoint:** todos os contract tests da Fase 3.2 devem passar.

---

## Fase 3.5 — Polish

- **T059 [P]** Testes unitários adicionais de domínio (modelo binário de cancelamento FR-007, cálculo `max(0, limite−sold−held)` FR-013). **Implementado em 2026-07-12**: `SeatCountTest` (novo — `vagasRestantes()`, incluindo o caso de redução retroativa do limite abaixo do já vendido, FR-015 do vessel-management/Opção C) e `CancellationPolicyTest` (3 casos novos de borda nos dois limites — 7 dias da compra e 48h do passeio — além dos 4 já existentes).
- **T060 [P]** Geração de documentação OpenAPI a partir dos controllers. **Implementado em 2026-07-12**: `OpenApiConfig` (mesmo padrão do vessel-management, springdoc já estava no `pom.xml`), introspecção automática dos `@RestController` em `/v3/api-docs`/`/swagger-ui.html`.
- **T061** Revisão de IAM — permissões mínimas por Lambda (least privilege). **Revisado em 2026-07-12**: as 3 roles (`api`, `operator_events_consumer`, `sweeper`) já eram separadas com policies escopadas por recurso (herdado da Fase 3.4); gap real encontrado e corrigido — `ses:SendEmail`/`ses:SendTemplatedEmail` usavam `Resource = "*"` nas duas Lambdas que enviam e-mail, agora restrito à identidade de domínio verificada (`aws_ses_domain_identity.transactional.arn`) quando `var.ses_domain` está configurado, só caindo para `"*"` no modo dev/local (mesma condicional já usada pelo próprio recurso de identidade).
- **T062** Teste de carga simulando pico de alta temporada sobre `POST /bookings/hold` (valida T018 em escala, decide se *provisioned concurrency* de T010 é necessária). **Implementado em 2026-07-12**: `T062_HoldEndpointLoadTest` — 50 holds concorrentes em vagas/embarcações distintas (mede throughput/latência, não correção sob disputa da mesma vaga, que já é T018), mede p95 sobre o mesmo contexto Spring/DynamoDB Local dos demais testes de integração. Deliberadamente fora da descoberta automática do Surefire (nome não termina em `Test`/`Tests`/`TestCase`) — é uma ferramenta de apoio a decisão de infraestrutura, não um gate de correção, roda sob demanda com `mvn test -Dtest=T062_HoldEndpointLoadTest`. Não mede cold start de Lambda real — só o handler já quente contra DynamoDB Local.
- **T063 [P]** README de operação do módulo (rodar local com DynamoDB Local + Testcontainers, sandbox do Pagar.me, deploy). **Implementado em 2026-07-12**: `booking/README.md`, mesmo formato do `vessel-management/README.md`.

---

## Notas de execução paralela

- Tarefas `[P]` dentro da mesma fase podem ser distribuídas entre desenvolvedores/agentes diferentes sem conflito de arquivo.
- A Fase 3.2 (testes) precisa ser concluída e os testes precisam **falhar** antes de iniciar a Fase 3.3 — gate de TDD do spec-kit.
- **T055 é a tarefa que fecha o penúltimo passo da Saga coreografada entre os dois módulos** (o último é T059 do lado vessel-management, que consome o evento publicado aqui). O contrato dos eventos (`booking.confirmed`/`booking.cancelled`/`booking.transferred`, incluindo `transferAttemptId`) já foi revisado e fechado em 2026-07-12, antes da Fase 3.1 — ver "Contrato da Saga" em `plan-booking.md`. T032 (`VesselTransferViable`) persiste o campo `id` recebido de `vessel.transfer.viable` (é o `transferAttemptId` ecoado depois em T054/T055). T059 do lado vessel-management **foi implementado em 2026-07-12**: aplica `disponivel=false`/motivo na embarcação de ORIGEM (nunca no destino) — decisão derivada do código de `CancelDayWithBookingsUseCase` (T046 do vessel-management), ver `plan-vessel-management.md`.
