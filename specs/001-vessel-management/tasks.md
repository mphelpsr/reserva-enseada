# Tasks — Módulo de Gestão de Embarcação (vessel-management)

**Baseado em:** `plan-vessel-management.md` + `spec-vessel-management.md`
**Convenção:** `[P]` = pode ser feita em paralelo com outras tarefas `[P]` (arquivos/módulos diferentes, sem dependência entre si). Tarefas sem `[P]` têm dependência sequencial explícita.

---

## Fase 3.1 — Setup (infraestrutura base)

- **T001** Configurar backend de estado do Terraform (S3 + DynamoDB lock) em `infra/backend.tf`
- **T002** Terraform: tabela DynamoDB `VesselManagement` com PK/SK conforme `plan-vessel-management.md` (Data Model) + GSI1 (`GSI1PK=OWNER#id`, `GSI1SK=VESSEL#id`)
- **T003** Terraform: Cognito User Pool do proprietário (isolado do pool do booking)
- **T004** Terraform: API Gateway HTTP API + integração com Lambda + autorizador Cognito
- **T005** Terraform: tópicos SNS — `vessel.availability.changed`, `vessel.seatlimit.changed`, `vessel.cancellation.operator-initiated`, `vessel.transfer.viable`
- **T006** Terraform: EventBridge Scheduler + Lambda para o job de recálculo de advisory (FR-006)
- **T006b** Terraform: fila SQS assinando os tópicos `booking.transferred`, `booking.cancelled` e `booking.confirmed`, publicados pelo módulo booking (suporte de infraestrutura para T059/T059b — `booking.confirmed` alimenta a réplica `ConfirmedBookingCount`, decisão de 2026-07-12 em `plan-vessel-management.md`)
- **T007** Scaffold do projeto Java (Spring Boot 3.x, Gradle/Maven), dependências: AWS SDK v2 Enhanced Client, Spring Cloud Function (adaptador Lambda)
- **T008** Habilitar Lambda SnapStart na configuração de deploy

**Checkpoint:** infraestrutura provisionável via `terraform apply` e projeto Java compila vazio, antes de escrever qualquer lógica.

---

## Fase 3.2 — Testes primeiro (TDD — devem falhar antes da Fase 3.3)

- **T009 [P]** Contract test: `POST /vessels` (FR-001, FR-009)
- **T010 [P]** Contract test: `PATCH /vessels/{id}` (FR-002)
- **T011 [P]** Contract test: `POST /vessels/{id}/transfer` (FR-002)
- **T012 [P]** Contract test: `PUT /vessels/{id}/availability/{data}/{tipoPasseio}` (FR-003, FR-004)
- **T013 [P]** Contract test: `PUT /vessels/{id}/rotation/{data}`, incluindo resposta 409 estruturada (FR-013, FR-014)
- **T014 [P]** Contract test: `PUT /vessels/{id}/seat-limit/{data}/{tipoPasseio}` (FR-015)
- **T015 [P]** Contract test: `GET /vessels/{id}/calendar` (visão consolidada)
- **T016 [P]** Contract test: `GET /vessels/{id}/advisory/{data}` (FR-006, FR-008)
- **T017 [P]** Integration test: conflito rodízio x Alto Mar — rodízio sempre prevalece, ação exige escolha explícita (FR-014, cenário 3b)
- **T018 [P]** Integration test: contador de padrão automático de vagas — 1ª e 2ª vez aplicam 10%, 3ª vez em diante = zero vagas (FR-015, cenários 6/6a)
- **T019 [P]** Integration test: embarcação não pode ficar `ativa` sem `payment_recebedor_id` válido (FR-016, cenário 7)
- **T020 [P]** Integration test: remoção de embarcação com reservas futuras exige transferência prévia (FR-002)
- **T021 [P]** Integration test: fluxo de cancelamento — tenta transferência na mesma frota, se não houver publica `vessel.cancellation.operator-initiated` (FR-007, Princípio VII)
- **T022 [P]** Teste de concorrência: duas escritas simultâneas de disponibilidade/rodízio no mesmo dia não geram estado inconsistente (`ConditionExpression`)

**Checkpoint:** todos os testes acima devem existir e falhar (endpoints/lógica ainda não implementados) antes de iniciar a Fase 3.3.

---

## Fase 3.3 — Core (domínio, repositórios, casos de uso — só após Fase 3.2 falhar)

### Domínio (entidades)
- **T023 [P]** `domain/vessel/Vessel.java`
- **T024 [P]** `domain/vessel/Owner.java` (inclui `paymentRecebedorId`, FR-016)
- **T025 [P]** `domain/availability/DeclaredAvailability.java`
- **T026 [P]** `domain/availability/TourType.java` (enum: `ALTO_MAR`, `ORLA`)
- **T027 [P]** `domain/availability/RotationSchedule.java`
- **T028 [P]** `domain/seatlimit/PlatformSeatLimit.java`
- **T029 [P]** `domain/seatlimit/DefaultSeatUsageCounter.java`
- **T030 [P]** `domain/advisory/WeatherTideAdvisory.java`
- **T031 [P]** `domain/cancellation/{VesselTransfer, BookingTransferAttempt, OperatorInitiatedCancellation}.java`
- **T031b [P]** `domain/bookingcount/ConfirmedBookingCount.java` — réplica local somente-leitura do nº de reservas confirmadas por dia/tipo de passeio, mantida via T059b (ver "Eventos Consumidos" em `plan-vessel-management.md`, decisão de 2026-07-12)

### Repositórios (infra/dynamodb — dependem de T002 e T023-T031)
- **T032** `VesselRepository` (Enhanced Client, mapeamento single-table)
- **T033** `AvailabilityRepository`
- **T034** `RotationScheduleRepository`
- **T035** `SeatLimitRepository` (inclui leitura/incremento do `DefaultSeatUsageCounter`)
- **T035b** `BookingCountRepository` — leitura de `ConfirmedBookingCount` por dia/tipo de passeio (GetItem) e agregada por embarcação (Query `BOOKINGCOUNT#` com filtro de datas futuras, para T040b); escrita restrita ao consumidor T059b
- **T036** `AdvisoryRepository` (leitura para API; escrita restrita ao job assíncrono)
- **T037** Query GSI1 para listar embarcações por proprietário (FR-010)

### Casos de uso (application/ — dependem da camada de repositórios)
- **T038** `RegisterVesselUseCase` (FR-001, FR-009) — valida `payment_recebedor_id` antes de permitir status `ativa` (FR-016)
- **T039** `UpdateVesselUseCase` (FR-002)
- **T040** `TransferVesselUseCase` (FR-002 — exige transferência antes de remoção com reservas futuras)
- **T040b** `RemoveVesselUseCase` (FR-002 — `DELETE /vessels/{id}`: consulta `BookingCountRepository` agregado da embarcação; zero reservas futuras confirmadas → remoção direta; caso contrário → 409, exigindo `TransferVesselUseCase` (T040) concluído antes de reenviar)
- **T041** `SetAvailabilityUseCase` (FR-003, FR-004) — ao marcar um dia como indisponível, consulta `BookingCountRepository`: contador zero → efeito imediato (FR-004); contador > 0 → não aplica a mudança diretamente, delega para `CancelDayWithBookingsUseCase` (T046, FR-007)
- **T042** `SetRotationScheduleUseCase` (FR-013) com verificação de conflito e resposta 409 estruturada (FR-014)
- **T043** `SetSeatLimitUseCase` (FR-015 — aplica Opção C: nunca bloqueia, calcula `max(0, limite−vendidas−retidas)`, controla contador de default 10%)
- **T044** `GetVesselCalendarUseCase` (leitura consolidada para o painel desktop)
- **T045** `GetAdvisoryUseCase` (FR-006, FR-008 — expõe advisory sem nunca alterar `DeclaredAvailability`, Princípio I)
- **T046** `CancelDayWithBookingsUseCase` (FR-007 — Saga leve: busca embarcação da mesma frota com vaga; se achar, publica `vessel.transfer.viable`; senão, publica `vessel.cancellation.operator-initiated`)

**Checkpoint:** testes da Fase 3.2 relativos a domínio/casos de uso (unitários) devem passar.

---

## Fase 3.4 — Integração (API, mensageria, external, auth)

- **T047** `VesselController` — `POST/PATCH /vessels`, `POST /vessels/{id}/transfer`, `DELETE /vessels/{id}` (T040b)
- **T048** `AvailabilityController` — `PUT /vessels/{id}/availability/...`, `PUT /vessels/{id}/rotation/...`
- **T049** `SeatLimitController` — `PUT /vessels/{id}/seat-limit/...`
- **T050** `CalendarController` — `GET /vessels/{id}/calendar`
- **T051** `AdvisoryController` — `GET /vessels/{id}/advisory/{data}`
- **T052** SNS Publisher: `vessel.availability.changed` (dispara em T041)
- **T053** SNS Publisher: `vessel.seatlimit.changed` (dispara em T043)
- **T054** SNS Publisher: `vessel.cancellation.operator-initiated` (dispara em T046)
- **T055** SNS Publisher: `vessel.transfer.viable` (dispara em T046)
- **T056** `StormglassClient` (infra/external) — integração com a API de maré/previsão
- **T057** `AdvisoryCalculationJob` — Lambda acionada pelo EventBridge Scheduler, popula `WeatherTideAdvisory` via `StormglassClient`, nunca escreve em `DeclaredAvailability` (Princípio I)
- **T058** Autorizador Cognito conectado ao API Gateway (T003/T004), validando token do proprietário em todas as rotas — já resolvido em infra/api_gateway.tf (T004): a rota `$default` já usa o autorizador JWT do Cognito criado em T003. Nenhuma validação adicional é feita em código de aplicação — a Lambda de API confia no que já passou pelo autorizador do API Gateway antes de chegar até ela.
- **T059** Consumidor SQS assinando os tópicos `booking.transferred` e `booking.cancelled`, publicados pelo módulo booking (ver `tasks-booking.md`, T053-T055). Fecha o passo final da Saga coreografada de cancelamento (Princípio VII). **Payload confirmado em 2026-07-12** (ver "Contrato da Saga" em `plan-vessel-management.md`): ambos os eventos carregam `transferAttemptId` (= `BookingTransferAttempt.id`), permitindo `GetItem` direto em `VESSEL#<vesselId>`/`TRANSFER#<transferAttemptId>` em vez de Query+filtro por (vesselId, data, tipoPasseio) — essa chave composta não é única o suficiente (uma segunda tentativa para o mesmo dia/tipo enquanto a primeira segue `VIABLE_PENDING` criaria um registro ambíguo). Ao receber `booking.cancelled` com `transferAttemptId` preenchido (nulo = desistência direta do comprador, sem tentativa de transferência envolvida), fecha o registro sem ação adicional de dados — a disponibilidade do dia original já havia sido tratada em T046. Ao receber `booking.transferred` (sempre com `transferAttemptId` preenchido), efetiva o desfecho — **decisão de implementação ainda em aberto** (não é mais sobre o contrato do evento, que já está fechado): a leitura mais provável, registrada em `plan-vessel-management.md`, é finalizar a embarcação de **origem** (aplicar o `disponivel=false` que ficou pendente durante a janela de 48h) em vez de escrever em `DeclaredAvailability`/`SEATLIMIT` do destino — confirmar isso ao iniciar esta tarefa, não assumir. Além do fechamento da Saga, `booking.cancelled` também decrementa `ConfirmedBookingCount` (T031b) via `BookingCountRepository` (T035b) — mesma responsabilidade de manutenção da réplica local que T059b, então ambos os handlers podem viver na mesma classe de listener.
- **T059b** Consumidor SQS assinando o tópico `booking.confirmed`, publicado pelo módulo booking (ver `tasks-booking.md`, T053). Mantém a réplica local `ConfirmedBookingCount` (T031b, `plan-vessel-management.md` — decisão de 2026-07-12): incrementa o contador de `VESSEL#id/BOOKINGCOUNT#data#tipoPasseio` a cada reserva confirmada. É o dado que `SetAvailabilityUseCase` (T041) e `RemoveVesselUseCase` (T040b) consultam para decidir entre FR-004 (efeito imediato) e FR-007 (Saga de transferência/cancelamento) — sem isso, o módulo não teria como saber se um dia tem reserva. **Implementado nesta fase**: `BookingEventsConsumer` (infrastructure/messaging), acionado pela mesma Lambda/fila `booking_events` que vai fechar T059 (infra/lambda.tf `aws_lambda_function.booking_events_consumer` + `aws_lambda_event_source_mapping`), roteando por atributo de mensagem `event-type`. O payload de `booking.confirmed` usado (`BookingConfirmedEventPayload`: vesselId/data/tipoPasseio) foi **confirmado em 2026-07-12** como contrato final (ver "Contrato da Saga" em `plan-vessel-management.md`) — não é mais proposta.

**Checkpoint:** todos os contract tests da Fase 3.2 devem passar.

---

## Fase 3.5 — Polish

- **T060 [P]** Testes unitários adicionais de regras de domínio (lógica do contador FR-015, lógica de conflito FR-014)
- **T061 [P]** Geração de documentação OpenAPI a partir dos controllers
- **T062** Revisão de IAM — permissões mínimas necessárias por Lambda (least privilege)
- **T063** Execução do teste de concorrência (T022) em pipeline de CI, não só localmente
- **T064 [P]** README de operação do módulo (como rodar local com DynamoDB Local + Testcontainers, como fazer deploy)

---

## Notas de execução paralela

- Tarefas `[P]` dentro da mesma fase podem ser distribuídas entre desenvolvedores/agentes diferentes sem conflito de arquivo.
- A Fase 3.2 (testes) **precisa** ser concluída e os testes precisam **falhar** antes de iniciar a Fase 3.3 — é o gate de TDD do spec-kit.
- T059 já tem contrato definido (eventos `booking.transferred`/`booking.cancelled` publicados em `tasks-booking.md`, T053-T055) — fecha o 4º e último passo da Saga coreografada de cancelamento entre os dois módulos. Validar o payload exato junto com a implementação de T055 no booking, para os dois lados ficarem sincronizados.
