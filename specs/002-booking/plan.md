# Plan — Módulo de Booking (Comprador de Tickets)

**Baseado em:** `spec-booking.md`
**Constitution:** v1.3.0
**Status:** rascunho

---

## Technical Context

| Item | Decisão |
|---|---|
| Linguagem | Java 21 (LTS) |
| Framework | Spring Boot 3.x |
| Persistência | DynamoDB — single-table design própria do módulo (não compartilha tabela com vessel-management), AWS SDK v2 Enhanced Client |
| Mensageria | SNS (publica eventos de booking) + SQS (consome eventos publicados por vessel-management) |
| Compute | AWS Lambda (via API Gateway HTTP API) — ver justificativa abaixo, diferente do racional usado em vessel-management |
| Job assíncrono | EventBridge Scheduler → Lambda "sweeper" para reconciliar holds expirados (ver Data Model) |
| Integração externa | Pagar.me (Stone) — gateway de pagamento com split nativo (cartão, Pix, boleto), repassando automaticamente a comissão da plataforma e o valor líquido a cada proprietário |
| IaC | Terraform (mesma decisão do vessel-management, para consistência entre módulos) |
| Testes | JUnit 5 + Testcontainers (DynamoDB Local) + testes de concorrência dedicados para FR-003/FR-004 |
| Frontend | React mobile-first (Princípio III) — plan próprio, fora deste documento |

**Justificativa da escolha de compute:** diferente do vessel-management (tráfego baixo e previsível), o booking sofre picos de concorrência reais — muitos compradores tentando reservar as últimas vagas de um dia popular ao mesmo tempo, especialmente em alta temporada. Lambda é a escolha certa aqui por um motivo diferente do módulo anterior: ele escala instantaneamente para o pico sem provisionamento prévio, o que é exatamente o padrão de tráfego de venda de ingressos (rajada, não carga constante). Para os endpoints de hold/checkout, se o cold start em Java se mostrar um problema em testes de carga, ativamos *provisioned concurrency* apenas nesses endpoints críticos durante janelas de alta demanda conhecidas — decisão que fica para o `tasks.md`/operação, não bloqueia este plano.

---

## Constitution Check

| Princípio | Como o plano atende |
|---|---|
| I — Decisão final é do proprietário | Booking só **lê** o advisory e a disponibilidade final publicados pelo vessel-management via evento — nunca recalcula ou sobrescreve. |
| II — Consistência dentro do que a plataforma controla | Escrita atômica na contagem de vagas (`TransactWriteItems` com `ConditionExpression`) é o núcleo técnico do módulo — ver Data Model. |
| III — Mobile-first | Frontend React mobile-first; API otimizada para latência baixa em conexões móveis (payloads enxutos, poucos round-trips no fluxo de checkout). |
| IV — Eventos como contrato | Booking consome eventos do vessel-management via SQS (nunca chama a API dele em tempo de checkout) e publica os próprios eventos de status de reserva. |
| VI — Simplicidade | Tabela própria (sem transação cross-tabela com vessel-management); limite de vagas é replicado localmente via evento, evitando chamada síncrona entre módulos no caminho crítico. |
| VII — Transferência antes de reembolso, transparente ao comprador | Consumo dos eventos `vessel.cancellation.operator-initiated` e `vessel.transfer.viable` implementa o fluxo completo (FR-008, FR-009), incluindo o prazo de 48h para resposta do comprador. |

---

## Project Structure

```
booking/
├── src/main/java/com/empresa/booking/
│   ├── api/                     # Controllers REST (Lambda handlers via API Gateway)
│   ├── application/             # Casos de uso (hold, confirm, cancel, consumir eventos)
│   ├── domain/
│   │   ├── booking/             # Booking, Buyer
│   │   ├── seathold/            # SeatHold, contagem local de vagas (limite/vendidas/retidas)
│   │   ├── cancellation/        # CancellationPolicy (FR-006/FR-007)
│   │   └── operatorevents/      # Modelos dos eventos consumidos do vessel-management
│   ├── infrastructure/
│   │   ├── dynamodb/            # Repositórios (Enhanced Client), mapeamento single-table
│   │   ├── messaging/           # Publishers SNS, listeners SQS (assinam os tópicos do vessel-management)
│   │   └── payment/             # Cliente do gateway de pagamento (ver Phase 0)
│   └── jobs/                    # Handler Lambda do sweeper de holds expirados (EventBridge Scheduler)
├── src/test/java/...
│   └── concurrency/              # Testes dedicados de overselling (FR-003)
└── infra/                        # Terraform
```

---

## Phase 0 — Research (decisões confirmadas em 2026-07-11)

1. **Gateway de pagamento**: **Pagar.me (Stone)**, confirmado — cobre cartão, Pix e boleto com split de pagamento nativo por seller/transação, é o gateway mais citado como padrão de marketplace no Brasil, e possui documentação oficial cobrindo o fluxo de estorno dentro do modelo de split (compatível com FR-006/FR-007). Split é tratado como requisito regulatório, não só técnico — a Circular 3.815/2016 do Banco Central torna a divisão automática de pagamento uma prática esperada quando a plataforma intermedeia pagamento entre comprador e múltiplos vendedores (proprietários). **Consequência cross-módulo**: cada proprietário precisa ser cadastrado como recebedor/subconta no Pagar.me — isso implica um novo atributo no cadastro do proprietário em `spec-vessel-management.md` (`payment_recebedor_id`), ainda não modelado lá — ver nota em Fora de Escopo.
2. **Segregação de usuários**: **pools separados**, confirmado — Cognito do booking (comprador) usa um User Pool próprio, distinto do usado pelo vessel-management (proprietário). Justificativa: são personas e fluxos de auth completamente diferentes (app mobile do comprador vs. painel desktop do proprietário), e isolar os pools evita acoplar o esquema de permissões de um módulo ao do outro — se um dia um mesmo CPF precisar ser proprietário e comprador, isso vira um vínculo de dados entre contas, não um usuário único compartilhando pool.
3. **Canal de notificação ao comprador** (FR-010): **e-mail via Amazon SES no MVP**, confirmado — mais simples de operar no início (Princípio VI), sem dependência de app mobile nativo/Firebase para o primeiro lançamento. Push mobile fica como evolução natural quando o app do comprador estiver mais maduro, reaproveitando os mesmos eventos (`booking.confirmed`, `booking.cancelled`, `booking.transferred`) já publicados via SNS — basta adicionar um novo consumidor, sem mudar a lógica de negócio.

---

## Phase 1 — Data Model (DynamoDB single-table, tabela própria do booking)

**Tabela:** `Booking`

| PK | SK | Uso |
|---|---|---|
| `VESSEL#<vesselId>` | `SEATCOUNT#<data>#<tipoPasseio>` | Réplica local do limite de vagas (via evento `vessel.seatlimit.changed`) + contadores `sold` e `held` |
| `HOLD#<holdId>` | `METADATA` | Retenção temporária (FR-004), com atributo TTL nativo do DynamoDB |
| `BOOKING#<bookingId>` | `METADATA` | Reserva confirmada |
| `VESSEL#<vesselId>` | `BOOKING#<data>#<bookingId>` | Índice para localizar todas as reservas de um dia/embarcação (necessário para FR-008/FR-009: quando chega um evento de cancelamento/transferência, o sistema precisa achar rápido quais reservas são afetadas) |
| `BUYER#<buyerId>` | `BOOKING#<bookingId>` | Item de índice para listar reservas do comprador (FR-011) |

**GSI1** (`GSI1PK = BUYER#<buyerId>`, `GSI1SK = BOOKING#<bookingId>`): suporta "minhas reservas" sem scan.

**Ponto técnico importante — TTL do DynamoDB não é confiável para liberar vagas a tempo:** o TTL nativo do DynamoDB só garante a exclusão do item dentro de até 48h após a expiração, não em segundos — inadequado para um hold de 10 minutos (FR-004). Por isso, o design usa duas camadas:
1. **Checagem em tempo de leitura/escrita**: ao calcular vagas restantes ou tentar um novo hold, a aplicação sempre ignora holds cujo `expiresAt` já passou, independentemente de o item já ter sido fisicamente removido pelo TTL sweep.
2. **Job sweeper periódico** (EventBridge Scheduler, a cada 1 minuto): varre holds expirados não convertidos em reserva e decrementa o contador `held` no item `SEATCOUNT`, mantendo o número exibido ao comprador sempre correto — o TTL nativo cuida só da limpeza física dos itens, não da lógica de negócio.

**Access patterns cobertos:**
1. Consultar vagas restantes de um dia/tipo de passeio → `GetItem(SEATCOUNT)`, calcula `max(0, limite − sold − held_não_expirado)` (FR-013, Opção C)
2. Criar hold → `TransactWriteItems`: `PutItem(HOLD, condição de não existir)` + `UpdateItem(SEATCOUNT, held += qty, ConditionExpression: limite − sold − held >= qty)` (FR-003, FR-004)
3. Confirmar reserva (pagamento aprovado) → `TransactWriteItems`: `PutItem(BOOKING)` + `DeleteItem(HOLD)` + `UpdateItem(SEATCOUNT, sold += qty, held -= qty)` (FR-005)
4. Liberar hold expirado (sweeper) → `UpdateItem(SEATCOUNT, held -= qty)` + `DeleteItem(HOLD)`
5. Listar reservas do comprador → `Query GSI1(BUYER#id)` (FR-011)
6. Localizar reservas afetadas por cancelamento/transferência de um dia → `Query(PK=VESSEL#id, SK begins_with BOOKING#<data>)` (FR-008, FR-009)

---

## Phase 1 — API (alto nível)

| Método | Rota | FR relacionado |
|---|---|---|
| GET | `/vessels/{id}/calendar?from=&to=` | FR-001, FR-002 (lê o read-model replicado do vessel-management) |
| POST | `/bookings/hold` | FR-003, FR-004 — cria hold, retorna `holdId` + `expiresAt` (10 min) |
| POST | `/bookings/{holdId}/confirm` | FR-005 — chamado após confirmação de pagamento |
| POST | `/bookings/{id}/cancel` | FR-006, FR-007 — cancelamento por desistência do comprador (modelo binário) |
| POST | `/bookings/{id}/respond-transfer` | FR-009 — aceitar transferência ou pedir reembolso, dentro da janela de 48h |
| GET | `/bookings` | FR-011 — histórico do comprador autenticado |
| GET | `/bookings/{id}` | Detalhe de uma reserva |

---

## Eventos Consumidos (SQS, assinando os tópicos SNS do vessel-management)

| Tópico (origem: vessel-management) | Efeito no booking |
|---|---|
| `vessel.availability.changed` | Atualiza o read-model de disponibilidade exibido em `/vessels/{id}/calendar` |
| `vessel.seatlimit.changed` | Atualiza o atributo `limite` no item `SEATCOUNT` correspondente (FR-013, sempre aceita, nunca fica negativo) |
| `vessel.cancellation.operator-initiated` | Dispara FR-008: localiza reservas afetadas, cancela com reembolso integral automático, notifica com o motivo real |
| `vessel.transfer.viable` | Dispara FR-009: notifica comprador, aguarda resposta até 48h, aplica prioridade do comprador se ele já tiver pedido cancelamento (ver spec) |

## Eventos Publicados (SNS)

| Tópico | Disparado por |
|---|---|
| `booking.confirmed` | FR-005 |
| `booking.cancelled` | FR-006/FR-007 (desistência) ou consumo de `vessel.cancellation.operator-initiated` |
| `booking.transferred` | Resposta positiva do comprador ao evento `vessel.transfer.viable` |

Esses tópicos ficam disponíveis para módulos futuros (ex.: relatórios, CRM) sem acoplar o booking a eles hoje — não há consumidor definido ainda, só o contrato publicado (Princípio IV).

---

## Contrato da Saga — revisão de 2026-07-12 (antes da Fase 3.1)

Revisão feita em conjunto com `plan-vessel-management.md` ("Contrato da Saga"), antes de iniciar a implementação deste módulo — os dois lados nascem alinhados em vez de descobrirem divergência só na Fase 3.4. Payload exato de cada evento, nos dois sentidos.

**Envelope de transporte (ambos os sentidos):** SNS → SQS sem raw message delivery — o corpo de cada mensagem SQS é o envelope de notificação do SNS, com o payload de negócio como string JSON dentro do campo `Message`; roteamento pelo atributo de mensagem `event-type`. Datas trafegam como string ISO-8601 (`yyyy-MM-dd`) no fio, independente do tipo Java usado em cada lado.

**Consumidos deste módulo, publicados pelo vessel-management (já implementados do lado publisher — `SnsEventListener`, T052-T055 de `tasks-vessel-management.md`):**

| Evento | Payload (nomes de campo exatos) |
|---|---|
| `vessel.availability.changed` | `{vesselId: string, data: "yyyy-MM-dd", tipoPasseio: "alto_mar"\|"orla", disponivel: boolean, motivo: string\|null}` |
| `vessel.seatlimit.changed` | `{vesselId: string, data: "yyyy-MM-dd", tipoPasseio: "alto_mar"\|"orla", limite: int}` |
| `vessel.cancellation.operator-initiated` | `{vesselId: string, data: "yyyy-MM-dd", tipoPasseio: "alto_mar"\|"orla", motivo: string}` |
| `vessel.transfer.viable` | `{id: string, vesselId: string, data: "yyyy-MM-dd", tipoPasseio: "alto_mar"\|"orla", targetVesselId: string, motivo: string}` — `id` identifica a tentativa de transferência do lado vessel-management; **guardar esse valor** (ex.: no próprio `BookingTransferAttempt`/registro local que T052 for criar) para ecoá-lo de volta em `booking.transferred`/`booking.cancelled` como `transferAttemptId` |

**Publicados por este módulo (T053-T055) — CONFIRMADOS nesta revisão, não mais proposta:**

| Evento | Payload | Observação |
|---|---|---|
| `booking.confirmed` (T053) | `{vesselId: string, data: "yyyy-MM-dd", tipoPasseio: "alto_mar"\|"orla"}` | O vessel-management já consome este contrato (T059b, implementado) — publicar exatamente estes 3 campos, sem envelope adicional |
| `booking.cancelled` (T054) | `{vesselId: string, data: "yyyy-MM-dd", tipoPasseio: "alto_mar"\|"orla", bookingId: string, transferAttemptId: string\|null}` | `transferAttemptId` = o `id` recebido em `vessel.transfer.viable`, só quando este cancelamento é a resolução (recusa explícita ou expiração das 48h) de uma oferta de transferência; em desistência direta do comprador (FR-006, sem transferência envolvida), enviar `null` |
| `booking.transferred` (T055) | `{vesselId: string, data: "yyyy-MM-dd", tipoPasseio: "alto_mar"\|"orla", bookingId: string, targetVesselId: string, transferAttemptId: string}` | `vesselId`/`data`/`tipoPasseio` são os da embarcação **original** (a que recebeu `vessel.transfer.viable`), não os do destino; `transferAttemptId` nunca é `null` aqui |

**Por que `transferAttemptId` é obrigatório para correlacionar, em vez de (vesselId, data, tipoPasseio):** essa chave composta pode não ser única no vessel-management ao longo do tempo (duas tentativas de transferência podem existir para o mesmo dia/tipo em momentos diferentes). Sempre que o booking responder a uma oferta de `vessel.transfer.viable` (aceitando ou recusando), ecoar o `id` recebido naquele evento evita essa ambiguidade do lado de quem consome.

---

## Fora de escopo deste plano

- Cálculo de disponibilidade final e regras de rodízio/maré (spec-vessel-management, já implementado naquele módulo).
- App mobile do comprador em si (plan de frontend próprio).

Ambas as pendências cross-módulo identificadas anteriormente (`payment_recebedor_id` no vessel-management e percentual de comissão) foram resolvidas — ver `spec-vessel-management.md` (FR-016) e `spec-booking.md` (FR-015).

---

## Próximo passo

Todos os itens da Phase 0 e as pendências cross-módulo estão resolvidos. Próximo passo: gerar o `tasks.md` do booking, quebrando cada endpoint/entidade em tarefas executáveis (schema DynamoDB, repositório, casos de uso, controllers, publishers/consumers SNS/SQS, integração Pagar.me, testes de concorrência).
