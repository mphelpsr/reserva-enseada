# CLAUDE.md

Contexto para qualquer sessão do Claude Code neste repositório. Leia isto antes de tocar em qualquer tarefa.

## O que é este projeto

Sistema de reservas de passeios em alto mar, com dois módulos:

- **vessel-management** (`specs/001-vessel-management/`) — painel do proprietário: cadastro de embarcações, disponibilidade, rodízio, limite de vagas.
- **booking** (`specs/002-booking/`) — app do comprador: consulta de disponibilidade, reserva, pagamento, cancelamento.

Cada módulo tem `spec.md` (requisitos), `plan.md` (arquitetura) e `tasks.md` (tarefas executáveis, numeradas T001, T002...). **`tasks.md` é a fonte da verdade de execução** — siga a ordem das fases e não pule o gate de TDD (Fase 3.2 precisa existir e falhar antes de escrever qualquer código da Fase 3.3).

## Antes de qualquer tarefa

1. Leia `.specify/memory/constitution.md` inteiro. Os 7 princípios ali (especialmente I, IV, VI, VII) não são sugestões — são restrições de design que todas as specs já respeitam. Se uma tarefa parecer pedir algo que viola um princípio, pare e sinalize em vez de implementar.
2. Leia o `spec.md` e `plan.md` do módulo antes do `tasks.md` correspondente — o `tasks.md` assume esse contexto e não repete o "porquê" de cada decisão.

## Pontos sensíveis — preste atenção redobrada

- **Princípio I (decisão do proprietário é final)**: o cálculo de maré/previsão (Stormglass) é só um *alerta* exibido ao proprietário. Nunca escreva código que sobrescreva `DeclaredAvailability` automaticamente com base nesse dado. A única indisponibilidade automática legítima é o rodízio (FR-013/FR-014), e mesmo essa só afeta Alto Mar, nunca Orla.
- **FR-014 (conflito rodízio x Alto Mar)**: rodízio sempre prevalece; a API responde 409 e exige escolha explícita do proprietário — nunca resolva o conflito silenciosamente no backend.
- **FR-013 do booking / FR-015 do vessel-management (limite de vagas)**: a redução do limite pelo proprietário NUNCA é bloqueada e NUNCA invalida reservas existentes (Opção C). O cálculo de vagas restantes é sempre `max(0, limite − vendidas − retidas)`. Não implemente validação síncrona entre os dois módulos para isso — é intencional que não exista.
- **Hold de vagas (FR-004 do booking)**: 10 minutos, e o TTL nativo do DynamoDB **não é suficiente sozinho** — ele só garante limpeza física em até 48h. Toda leitura de vagas restantes precisa ignorar holds com `expiresAt` vencido na aplicação, independente do item ainda existir fisicamente. O job sweeper (a cada 1 min) é reforço de higiene, não a fonte da verdade de consistência.
- **Split de pagamento (FR-015 do booking, FR-016 do vessel-management)**: toda embarcação precisa de `payment_recebedor_id` válido antes de poder ficar `ativa`. Comissão da plataforma é 12%, configurável — nunca hardcoded num valor fixo no código sem ponto de configuração.
- **Cancelamento — modelo binário (FR-006/FR-007 do booking)**: não implemente reembolso parcial/escalonado por desistência do comprador. É tudo ou nada, dentro ou fora da janela (7 dias da compra / 48h antes do passeio).
- **A Saga entre os dois módulos (Princípio VII)** é coreografada, não orquestrada — não crie um serviço central coordenando os dois. Os 4 passos:
  1. vessel-management publica `vessel.transfer.viable` ou `vessel.cancellation.operator-initiated`
  2. booking consome, notifica o comprador, aguarda até 48h
  3. booking decide (aceite/timeout) e publica `booking.transferred` ou `booking.cancelled`
  4. vessel-management consome esse evento (T059 do `tasks-vessel-management` / T055 do `tasks-booking`) e efetiva o resultado final
  Ao implementar T055 e T059, o payload do evento precisa ser combinado entre as duas implementações — são a mesma tarefa vista dos dois lados.
- **Prioridade em corrida de eventos**: se o comprador já pediu cancelamento antes/durante a chegada de uma oferta de transferência, o cancelamento do comprador sempre vence (FR-009 do booking).

## Stack e convenções técnicas

- Java 21, Spring Boot 3.x, AWS SDK v2 Enhanced Client
- DynamoDB single-table por módulo (schemas em cada `plan.md`, seção "Phase 1 — Data Model")
- SNS para publicar eventos de domínio, SQS para consumir — nunca chamada HTTP síncrona entre os dois módulos
- Lambda (SnapStart habilitado) como compute, Terraform como IaC
- Testes: JUnit 5 + Testcontainers (DynamoDB Local); testes de concorrência são obrigatórios onde a spec menciona escrita condicional (`ConditionExpression`/`TransactWriteItems`)

## Como trabalhar as tarefas

- Siga a ordem das fases dentro de cada `tasks.md`. Tarefas marcadas `[P]` podem ser paralelizadas entre si; as demais têm dependência sequencial implícita pela ordem em que aparecem.
- Cada checkpoint de fase (ex.: "todos os contract tests devem passar") é um gate real — não avance pra próxima fase sem ele satisfeito.
- Ao terminar uma tarefa, comite com mensagem referenciando o ID (`git commit -m "T038: RegisterVesselUseCase (FR-001, FR-009, FR-016)"`) — facilita rastrear no PR depois.
- Se uma tarefa depender de algo não resolvido em nenhuma spec/plan (como aconteceu com T059 antes de ser formalizada), pare e pergunte em vez de assumir um comportamento.
