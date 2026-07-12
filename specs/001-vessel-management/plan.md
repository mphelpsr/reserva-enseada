# Plan — Módulo de Gestão de Embarcação (vessel-management)

**Baseado em:** `spec-vessel-management.md`
**Constitution:** v1.3.0
**Status:** rascunho

---

## Technical Context

| Item | Decisão |
|---|---|
| Linguagem | Java 21 (LTS) |
| Framework | Spring Boot 3.x |
| Persistência | DynamoDB — single-table design, AWS SDK v2 Enhanced Client |
| Mensageria | SNS (publica eventos de domínio) + SQS (consumidores internos, ex.: job de recálculo de advisory) |
| Compute | AWS Lambda (via API Gateway HTTP API) + Lambda SnapStart para mitigar cold start em Java |
| Job assíncrono | EventBridge Scheduler → Lambda, para recalcular o indicador de maré/previsão (FR-006) periodicamente |
| Integração externa | Stormglass (maré + previsão do tempo marítima, contrato único) — consumido por um job assíncrono que gera o WeatherTideAdvisory (FR-006), não bloqueante |
| Testes | JUnit 5 + Testcontainers (DynamoDB Local) + testes de contrato para os eventos SNS |
| Frontend | React desktop-first (Princípio III) — plan próprio, fora deste documento |

**Justificativa da escolha de compute (Lambda vs. container):** o módulo de gestão tem tráfego proporcional ao número de proprietários operando o painel — baixo volume, uso concentrado em horário comercial, picos previsíveis (não como o módulo de booking, que sofre pico de concorrência em vendas). Lambda com SnapStart elimina custo de infraestrutura ociosa e complexidade operacional de manter um cluster, alinhado ao Princípio VI (simplicidade antes de escalabilidade prematura). Se o volume crescer a ponto de Lambda deixar de ser adequado (ex.: latência inaceitável mesmo com SnapStart), a migração para ECS Fargate é direta, já que a lógica de domínio fica isolada da camada de entrada HTTP.

---

## Constitution Check

| Princípio | Como o plano atende |
|---|---|
| I — Decisão final é do proprietário | Advisory (maré/previsão) é calculado por job assíncrono separado e nunca escreve em `DeclaredAvailability`; API não expõe endpoint que force esse valor. |
| II — Consistência dentro do que a plataforma controla | Escritas em `DeclaredAvailability`, `PlatformSeatLimit` e `RotationSchedule` usam condição condicional do DynamoDB (`ConditionExpression`) para evitar race conditions entre atualizações concorrentes do mesmo proprietário. |
| III — Desktop-first + mobile companion | Este plano cobre só a API/domínio; o dashboard mobile "light" (Princípio III) é uma spec e plan futuros, consumindo os mesmos endpoints de leitura. |
| IV — Eventos como contrato | Toda mudança de estado relevante publica em um tópico SNS dedicado (ver seção de Eventos); nenhum acoplamento síncrono com o módulo booking. |
| V — Regras evoluem | Regras de negócio (rodízio, limite de vagas, transferência) ficam isoladas na camada de domínio (`domain/`), não espalhadas em controllers. |
| VI — Simplicidade | Lambda + single-table DynamoDB evita infraestrutura desnecessária para o volume atual. |
| VII — Transferência antes de reembolso | `VesselTransfer`/`BookingTransferAttempt` implementados como Saga leve orquestrada por eventos (detalhado abaixo), sem chamada síncrona ao módulo booking. |

---

## Project Structure

```
vessel-management/
├── src/main/java/com/empresa/vesselmanagement/
│   ├── api/                     # Controllers REST (Lambda handlers via API Gateway)
│   ├── application/             # Casos de uso (orquestram domínio + infra)
│   ├── domain/
│   │   ├── vessel/              # Vessel, Owner
│   │   ├── availability/        # DeclaredAvailability, TourType, RotationSchedule
│   │   ├── seatlimit/           # PlatformSeatLimit, DefaultSeatUsageCounter
│   │   ├── advisory/            # WeatherTideAdvisory (leitura, nunca escreve availability)
│   │   └── cancellation/        # VesselTransfer, BookingTransferAttempt, OperatorInitiatedCancellation
│   ├── infrastructure/
│   │   ├── dynamodb/            # Repositórios (Enhanced Client), mapeamento single-table
│   │   ├── messaging/           # Publishers SNS, listeners SQS
│   │   └── external/            # Clientes da API de maré/previsão
│   └── jobs/                    # Handler Lambda do EventBridge Scheduler (recálculo de advisory)
├── src/test/java/...
└── infra/                       # Terraform (módulos: DynamoDB, Lambda, API Gateway, SNS/SQS, Cognito, EventBridge Scheduler)
```

---

## Phase 0 — Research (decisões confirmadas em 2026-07-11)

1. **Provedor de dados de maré/previsão**: **Stormglass** — único provedor pesquisado que combina maré e previsão do tempo marítima num único contrato/integração (fontes: NOAA, Météo-France, UK Met Office, DWD), com preço acessível para o volume inicial (€19/mês). Alternativas descartadas: WorldTides (só maré, exigiria segundo provedor pra clima) e Open-Meteo (mais barato, mas o próprio fornecedor desaconselha o dado de maré para decisões de navegação costeira, por causa da resolução de 8km). A tábua de maré oficial da Marinha do Brasil (DHN) pode ser referenciada como fonte legal/institucional complementar no futuro, mas não tem API REST acessível hoje.
2. **Autenticação/autorização do proprietário**: **Amazon Cognito**, confirmado — integra nativamente com API Gateway, evita reimplementar gestão de usuários/sessão.
3. **Ferramenta de IaC**: **Terraform**, confirmado — mesmo usando AWS hoje, Terraform mantém a infraestrutura desacoplada do provedor: os módulos ficam escritos em HCL (não amarrados a uma SDK específica da AWS como o CDK), o que facilita tanto migrar recursos para outro provedor no futuro quanto operar multi-cloud se necessário. Trade-off aceito conscientemente: introduz uma segunda linguagem (HCL) além do Java do backend — mas o ganho de portabilidade compensa, dado que "evitar lock-in de provedor" é um requisito explícito seu.

---

## Phase 1 — Data Model (DynamoDB single-table)

**Tabela:** `VesselManagement`

| PK | SK | Uso |
|---|---|---|
| `VESSEL#<vesselId>` | `METADATA` | Dados cadastrais da embarcação (FR-001, FR-009, FR-011) |
| `VESSEL#<vesselId>` | `AVAIL#<data>#<tipoPasseio>` | Disponibilidade declarada (FR-003) |
| `VESSEL#<vesselId>` | `ROTATION#<data>` | Escala de rodízio (FR-013) |
| `VESSEL#<vesselId>` | `SEATLIMIT#<data>#<tipoPasseio>` | Limite de vagas na plataforma (FR-015) |
| `VESSEL#<vesselId>` | `COUNTER#DEFAULTSEAT` | Contador cumulativo do padrão automático de 10% (FR-015) |
| `VESSEL#<vesselId>` | `ADVISORY#<data>` | Indicador de maré/previsão, gerado pelo job assíncrono (FR-006) |
| `VESSEL#<vesselId>` | `TRANSFER#<transferId>` | Registro de tentativa/execução de transferência (FR-002, FR-007) |
| `VESSEL#<vesselId>` | `BOOKINGCOUNT#<data>#<tipoPasseio>` | Réplica local do nº de reservas confirmadas naquele dia/tipo de passeio, mantida via consumo dos eventos `booking.confirmed`/`booking.cancelled` publicados pelo módulo booking (ver "Eventos Consumidos" abaixo). Único propósito: decidir entre FR-004 (efeito imediato, contador zero) e FR-007 (Saga de transferência/cancelamento, contador > 0) ao tornar um dia indisponível ou remover uma embarcação — não é fonte de verdade de reservas, essa é do módulo booking |
| `OWNER#<ownerId>` | `METADATA` | Dados cadastrais do proprietário, incluindo `payment_recebedor_id` (FR-016) |
| `OWNER#<ownerId>` | `VESSEL#<vesselId>` | Item de índice para listar embarcações por proprietário |

**GSI1** (`GSI1PK = OWNER#<ownerId>`, `GSI1SK = VESSEL#<vesselId>`): suporta o access pattern "listar embarcações de um proprietário" (FR-010) sem scan.

**Access patterns cobertos:**
1. Obter embarcação por ID → `GetItem(VESSEL#id, METADATA)`
2. Listar embarcações de um proprietário → `Query GSI1(OWNER#id)`
3. Obter/atualizar disponibilidade de um dia/tipo de passeio → `GetItem`/`PutItem` com `ConditionExpression`
4. Calendário de disponibilidade de uma embarcação (intervalo de datas) → `Query(PK=VESSEL#id, SK begins_with AVAIL#)` com filtro de range
5. Verificar conflito rodízio x disponibilidade Alto Mar (FR-014) → leitura combinada de `ROTATION#<data>` e `AVAIL#<data>#alto_mar` na mesma transação de escrita (`TransactWriteItems` com `ConditionCheck`)
6. Obter/atualizar limite de vagas → `GetItem`/`PutItem`, sem `ConditionExpression` bloqueante (FR-013, Opção C — sempre aceita)
7. Obter advisory do dia → `GetItem(VESSEL#id, ADVISORY#data)`, escrito só pelo job assíncrono
8. Verificar se um dia/tipo de passeio tem reserva confirmada, antes de aplicar FR-004 (efeito imediato) ou FR-007 (Saga) → `GetItem(VESSEL#id, BOOKINGCOUNT#data#tipoPasseio)`; mesma checagem, agregada por embarcação, para a remoção de embarcação (FR-002) via `Query(PK=VESSEL#id, SK begins_with BOOKINGCOUNT#)` filtrando datas futuras

---

## Phase 1 — API (alto nível)

| Método | Rota | FR relacionado |
|---|---|---|
| POST | `/vessels` | FR-001 |
| PATCH | `/vessels/{id}` | FR-002 |
| POST | `/vessels/{id}/transfer` | FR-002 |
| PUT | `/vessels/{id}/availability/{data}/{tipoPasseio}` | FR-003, FR-004 |
| PUT | `/vessels/{id}/rotation/{data}` | FR-013, FR-014 (retorna 409 + payload de conflito se Alto Mar já disponível nesse dia) |
| PUT | `/vessels/{id}/seat-limit/{data}/{tipoPasseio}` | FR-015 |
| GET | `/vessels/{id}/calendar?from=&to=` | Visão consolidada para o painel desktop |
| GET | `/vessels/{id}/advisory/{data}` | FR-006, FR-008 |

O conflito de FR-014 (rodízio x Alto Mar) é resolvido com uma resposta HTTP 409 estruturada, contendo as duas opções (mudar disponibilidade ou mudar rodízio) — o frontend decide a exibição, mas a escolha explícita do proprietário é exigida antes de reenviar a requisição.

---

## Eventos Publicados (SNS)

| Tópico | Disparado por | Consumido por (fora deste módulo) |
|---|---|---|
| `vessel.availability.changed` | FR-003, FR-004, FR-013 | booking (recalcula o que exibir ao comprador) |
| `vessel.seatlimit.changed` | FR-015 | booking (recalcula vagas restantes: `max(0, limite − vendidas − retidas)`) |
| `vessel.cancellation.operator-initiated` | FR-007 (após tentativa de transferência falhar) | booking (FR-008 do spec-booking: reembolso integral automático) |
| `vessel.transfer.viable` | FR-007 (transferência encontrada) | booking (FR-009 do spec-booking: notifica comprador, exige confirmação) |

**Fluxo de cancelamento (Princípio VII) como Saga leve:**
1. Proprietário tenta tornar dia indisponível com reservas confirmadas.
2. vessel-management busca outra embarcação do mesmo proprietário com vaga (query local, sem chamar booking).
3. Se encontrar → publica `vessel.transfer.viable` e aguarda; não altera a disponibilidade original até confirmação do comprador (evento de retorno do booking, fora do escopo deste plano).
4. Se não encontrar → publica `vessel.cancellation.operator-initiated` imediatamente, com o motivo estruturado (FR-007).

---

## Eventos Consumidos (SQS)

Decisão registrada em 2026-07-12 (mesma lacuna que motivou T059 antes de ser formalizada — ver `tasks-vessel-management.md`): FR-007 exige que o sistema distinga um dia sem reserva (FR-004, efeito imediato) de um dia com reserva confirmada (Saga de transferência/cancelamento), mas o módulo não tem nenhuma visibilidade sobre reservas — esse dado pertence ao módulo booking. Resolvido com réplica local somente-leitura, no mesmo padrão que o booking já usa para replicar disponibilidade/limite de vagas do vessel-management (Princípio IV).

| Tópico (origem: booking) | Consumido para | Efeito |
|---|---|---|
| `booking.confirmed` | Manter `BOOKINGCOUNT#<data>#<tipoPasseio>` | Incrementa o contador da réplica local |
| `booking.cancelled` | Manter `BOOKINGCOUNT#<data>#<tipoPasseio>` + fechar tentativa de transferência (T059) | Decrementa o contador da réplica local; se a origem for uma oferta de transferência (`vessel.transfer.viable`) não aceita/expirada, também fecha o registro em `TRANSFER#<transferId>` (T059, sem ação adicional de dados) |
| `booking.transferred` | Fechar tentativa de transferência (T059) | Move a reserva para a embarcação/dia de destino combinado, atualizando `DeclaredAvailability`/`SEATLIMIT` do destino — não afeta `BOOKINGCOUNT` da embarcação original (a reserva não foi cancelada, só migrou de embarcação) |

Ambos os tópicos já existem no módulo booking (`booking.cancelled`, `booking.transferred` — ver `plan-booking.md`, T053-T055); `booking.confirmed` é consumido aqui pela primeira vez especificamente para popular `BOOKINGCOUNT`. A fila SQS de suporte (T006b) assina os três, entregues a uma única Lambda (`booking_events_consumer`, infra/lambda.tf) que roteia por atributo de mensagem `event-type` (mesma convenção do publisher, `SnsEventListener`).

**Payload de `booking.confirmed` (proposto por este módulo em T059b, a confirmar com `tasks-booking.md` T053):** `{ "vesselId": string, "data": "yyyy-MM-dd", "tipoPasseio": "alto_mar"|"orla" }`.

---

## Fora de escopo deste plano

- Lógica de decremento de vagas e checkout (spec-booking, plan próprio).
- Dashboard mobile do proprietário (spec e plan futuros, Princípio III).

---

## Próximo passo

Gerar `tasks.md` a partir deste plano, quebrando cada endpoint/entidade em tarefas executáveis (schema DynamoDB, repositório, caso de uso, controller, publisher SNS, testes).
