# Spec — Módulo de Booking (Comprador de Tickets)

**Feature:** booking
**Depende de (constitution):** Princípios I (decisão final é do proprietário / dado externo é apoio), II (consistência dentro dos canais controlados), III (mobile-first para o comprador), IV (eventos), VII (transferência antes de reembolso, sempre transparente)
**Depende de (spec):** `spec-vessel-management.md` — consome disponibilidade final, limite de vagas (PlatformSeatLimit) e eventos de transferência/cancelamento publicados por aquele módulo.
**Status:** rascunho

---

## User Scenarios & Testing

### Primary User Story
Como comprador, eu quero visualizar em tempo real os dias e tipos de passeio (Alto Mar / Orla) disponíveis de uma embarcação — com vagas restantes e informação de maré/previsão — para reservar e pagar meu passeio com confiança de que a vaga é real e a transação é segura.

### Acceptance Scenarios

1. **Given** um comprador navegando pelas embarcações,
   **When** ele seleciona uma embarcação e um período,
   **Then** o sistema exibe os dias disponíveis por tipo de passeio (Alto Mar/Orla), com vagas restantes calculadas a partir do limite definido pelo proprietário (PlatformSeatLimit) menos as reservas já confirmadas — refletindo a disponibilidade final do módulo vessel-management, não um cálculo próprio.

2. **Given** um dia disponível com vagas restantes,
   **When** o comprador seleciona a quantidade de passageiros dentro do limite e confirma o pagamento,
   **Then** o sistema reserva as vagas de forma atômica (impedindo overselling por concorrência), confirma a reserva somente após pagamento bem-sucedido, e envia confirmação ao comprador.

3. **Given** dois compradores tentando reservar simultaneamente as últimas vagas restantes de um dia,
   **When** ambos confirmam a compra ao mesmo tempo,
   **Then** apenas um consegue concluir a reserva; o outro recebe erro imediato com a quantidade real de vagas restantes (sem overselling).

4. **Given** uma reserva confirmada há menos de 7 dias corridos e antes da data do passeio,
   **When** o comprador solicita cancelamento,
   **Then** o sistema aplica o direito de arrependimento (Art. 49 CDC) — reembolso integral automático, sem necessidade de justificativa.

5. **Given** uma reserva confirmada fora da janela de arrependimento (FR-006),
   **When** o comprador solicita cancelamento por desistência própria,
   **Then** o sistema recusa o cancelamento — não há reembolso parcial ou escalonado fora dessa janela (modelo binário, benchmark Sympla/Tickets for Fun).

6. **Given** o módulo vessel-management publica um evento de cancelamento por força maior/operador (indisponibilidade da embarcação sem transferência possível),
   **When** o módulo de booking consome esse evento,
   **Then** o sistema dispara reembolso integral automático imediato ao comprador e comunica explicitamente o motivo real (não uma mensagem genérica) — ver Princípio VII.

7. **Given** o módulo vessel-management publica um evento de transferência viável (outra embarcação do mesmo proprietário com vaga),
   **When** o módulo de booking consome esse evento,
   **Then** o sistema notifica o comprador com as novas condições (nova embarcação/data) e pede confirmação explícita: aceitar a transferência ou solicitar reembolso integral. Se o comprador não responder em até 48 horas, o sistema cancela automaticamente com reembolso integral.

8. **Given** um comprador com reservas passadas e futuras,
   **When** ele acessa sua área de reservas,
   **Then** o sistema exibe o histórico e status atual de cada reserva (pendente, confirmada, cancelada, transferida).

9. **Given** um dia disponível com vagas restantes cujo horário de saída está a menos de 24 horas,
   **When** um comprador tenta reservar,
   **Then** o sistema recusa a compra e informa que o prazo mínimo de antecedência (24h) não foi respeitado.

### Edge Cases

- Pagamento iniciado mas não concluído dentro do tempo de retenção (hold) — vagas devem voltar a ficar disponíveis automaticamente (FR-004).
- Proprietário reduz o limite de vagas (PlatformSeatLimit) no vessel-management para um valor abaixo do que já foi vendido naquele dia — **resolvido (ver FR-013)**: a edição é sempre aceita sem bloqueio; vagas restantes nunca ficam negativas e reservas já confirmadas não são afetadas.
- Comprador tenta cancelar uma reserva de um dia que já passou — não aplicável, deve ser bloqueado.
- Evento de transferência chega para uma reserva que o comprador já havia solicitado cancelamento (corrida entre eventos) — **resolvido (ver FR-009)**: o cancelamento do comprador sempre prevalece.
- Comprador reserva Alto Mar e, antes da data, o proprietário cadastra rodízio para aquele mesmo dia/embarcação (conflito já resolvido no vessel-management via FR-014, que impede a coexistência) — booking só recebe o resultado final, não precisa tratar esse conflito diretamente.

---

## Requirements

### Functional Requirements

- **FR-001**: O sistema DEVE exibir, em tempo real, os dias e tipos de passeio disponíveis por embarcação, refletindo a disponibilidade final declarada no módulo vessel-management e as vagas restantes (PlatformSeatLimit menos reservas confirmadas/retidas).
- **FR-002**: O sistema DEVE exibir ao comprador o mesmo indicador de apoio de maré/previsão visto pelo proprietário, como informação complementar da data — nunca como filtro que oculta ou bloqueia a visualização de uma data disponível (consistente com o Princípio I: a decisão de disponibilizar já foi tomada pelo proprietário).
- **FR-003**: O sistema DEVE impedir reserva de quantidade de passageiros maior que as vagas restantes, usando escrita condicional atômica no DynamoDB para evitar overselling em cenários de concorrência (Princípio II).
- **FR-004**: O sistema DEVE reter temporariamente (hold) as vagas selecionadas durante o processo de pagamento, por um tempo máximo de **10 minutos**, liberando-as automaticamente se o pagamento não for concluído dentro desse prazo. Essa retenção DEVE ser uma trava síncrona sobre a contagem de vagas (mesmo em uma arquitetura predominantemente assíncrona/orientada a eventos) — é o único ponto do sistema onde consistência imediata é mais importante que desacoplamento, dado o risco de concorrência em períodos de alta demanda (alta temporada). Benchmark: operadores de passeio marítimo no Brasil praticam holds ainda mais curtos (ex.: 15 minutos).
- **FR-005**: O sistema DEVE confirmar a reserva e decrementar as vagas definitivamente somente após confirmação de pagamento bem-sucedida.
- **FR-006**: O sistema DEVE aplicar o direito de arrependimento (Art. 49 CDC): reembolso integral automático, sem necessidade de justificativa, para cancelamento solicitado pelo comprador em até 7 dias corridos da compra e antes da data do passeio. Se a compra ocorrer a menos de 7 dias do passeio, o prazo de cancelamento fica comprimido para até 48 horas antes do início do passeio (modelo alinhado ao benchmark Sympla/Tickets for Fun).
- **FR-007**: Fora da janela de FR-006, o sistema NÃO DEVE permitir cancelamento por desistência do próprio comprador — sem reembolso parcial ou escalonado (benchmark: nem Sympla nem Tickets for Fun oferecem reembolso parcial por desistência fora do prazo legal; é um modelo binário, não escalonado). O botão/opção de cancelamento por desistência deve ficar indisponível ao comprador fora dessa janela.
- **FR-008**: O sistema DEVE consumir o evento de cancelamento por força maior/operador publicado pelo vessel-management (quando a transferência não foi possível) e disparar reembolso integral automático imediato, comunicando ao comprador o motivo real e específico do cancelamento (Princípio VII) — nunca uma mensagem genérica.
- **FR-009**: O sistema DEVE consumir eventos de transferência viável publicados pelo vessel-management e notificar o comprador com as novas condições, exigindo confirmação explícita: aceitar a transferência ou solicitar reembolso integral. Se não houver resposta em até 48 horas, o sistema DEVE cancelar automaticamente com reembolso integral. Se o comprador já tiver solicitado cancelamento (FR-006) antes ou no momento em que o evento de transferência chegar, o cancelamento do comprador SEMPRE prevalece — a oferta de transferência é descartada e o reembolso integral segue normalmente (alinhado ao entendimento judicial no caso T4F/Taylor Swift, que garantiu o direito ao reembolso em vez de reacomodação forçada).
- **FR-010**: O sistema DEVE notificar o comprador em cada mudança relevante de status da reserva (confirmada, cancelada, transferida, reembolsada) via canal apropriado.
- **FR-011**: O sistema DEVE permitir que o comprador consulte seu histórico de reservas passadas e futuras, com status atual de cada uma.
- **FR-012**: A interface do comprador DEVE ser mobile-first (Princípio III).
- **FR-013**: O sistema DEVE permitir que o proprietário reduza o limite de vagas (PlatformSeatLimit, definido no vessel-management) a qualquer momento, mesmo abaixo da quantidade já vendida/retida para aquele dia — sem chamada síncrona entre módulos e sem validação bloqueante no momento da edição (Princípio IV/VI). O cálculo de vagas restantes no booking DEVE ser sempre `max(0, limite − vendidas − retidas)`, nunca negativo. Reservas já confirmadas NUNCA são invalidadas ou canceladas retroativamente por essa redução — o efeito prático é apenas impedir novas vendas até que o número volte a ficar positivo (Princípio II permanece garantido, sem necessidade de Saga ou compensação).
- **FR-014**: O sistema NÃO DEVE permitir a compra de um passeio em menos de **24 horas** antes do horário de saída previsto. Esse prazo mínimo existe para dar previsibilidade operacional ao proprietário (confirmação de tripulação, grupo mínimo) e para se alinhar à janela em que a previsão do tempo é mais confiável — diferente da tábua de maré, que é determinística e conhecida com meses de antecedência, a previsão meteorológica ganha precisão nas horas mais próximas da saída. Benchmark: operadores de passeio marítimo no Brasil trabalham tipicamente com janelas de 24 a 48 horas de antecedência mínima para reserva.
- **FR-015**: O sistema DEVE aplicar uma comissão da plataforma de **12%** sobre o valor de cada venda confirmada, usada como regra de split no gateway de pagamento (Pagar.me — ver `plan-booking.md`): o proprietário recebe automaticamente 88% do valor líquido, a plataforma recebe 12%, ambos na mesma transação. O percentual DEVE ser um valor configurável globalmente (não hardcoded), dentro da faixa de 10-15% definida como padrão de mercado para marketplaces de baixo atrito — mudanças nesse percentual são uma decisão de negócio, não uma alteração de código.

  > **Nota de 2026-07-12 — revisão de modelo planejada, ainda NÃO implementada:** decidido nesta data que o proprietário não deve ser obrigado a ter subconta própria no Pagar.me — o modelo pretendido é a plataforma receber 100% numa conta única (CNPJ próprio) e repassar o valor líquido ao proprietário por fora do gateway (mecanismo de repasse ainda por desenhar), em vez do split automático descrito acima. **Este FR ainda descreve o comportamento ATUAL, realmente implementado** (`PagarmeClient`, T026/`PaymentSplitIntegrationTest`) — a mudança não foi codificada, só registrada como decisão de produto pendente de desenho. Ver nota equivalente em `spec-vessel-management.md` (FR-016) e a atualização de escopo em `plan.md`.

### Key Entities

- **Reserva (Booking)**: registro de compra de um comprador para um dia/tipo de passeio de uma embarcação específica. Atributos-chave: comprador, embarcação, dia, tipo de passeio, quantidade de passageiros, status (`retida`, `confirmada`, `cancelada`, `transferida`, `reembolsada`), valor pago, valor da comissão da plataforma, valor líquido repassado ao proprietário (FR-015).
- **Comprador (Buyer)**: pessoa que realiza a compra. Atributos-chave: identificação, contato, histórico de reservas.
- **Retenção Temporária (SeatHold)**: bloqueio temporário de vagas durante o checkout, com expiração automática (FR-004).
- **Política de Cancelamento (CancellationPolicy)**: regra binária aplicável a cancelamento por iniciativa do comprador — direito de arrependimento dentro da janela (FR-006) ou recusa fora dela (FR-007), sem escalonamento. Distinta do fluxo de cancelamento por iniciativa do proprietário/operação (Princípio VII, tratado via eventos consumidos de vessel-management).
- **Evento Consumido — Cancelamento por Operador (OperatorInitiatedCancellation)**: evento publicado pelo vessel-management, consumido aqui para disparar reembolso integral automático (FR-008).
- **Evento Consumido — Tentativa de Transferência (BookingTransferAttempt)**: evento publicado pelo vessel-management quando uma transferência é viável; consumido aqui para notificar e obter confirmação do comprador (FR-009).

---

## Decisões registradas (resolvidas em 2026-07-11, com benchmark Sympla/Tickets for Fun)

1. **Política de cancelamento por desistência do comprador:** modelo binário, não escalonado — dentro de 7 dias corridos da compra (ou até 48h antes do passeio, se comprado em cima da hora) = reembolso integral automático; fora disso = sem cancelamento pelo comprador. Nenhum reembolso parcial fora da janela legal, seguindo o padrão observado em Sympla e Tickets for Fun.
2. **Corrida entre cancelamento do comprador e transferência do proprietário:** o cancelamento do comprador sempre prevalece — alinhado ao precedente judicial do caso T4F/Taylor Swift, que garantiu o direito ao reembolso em vez de reacomodação forçada.
3. **Retenção de vagas durante checkout:** trava síncrona de no máximo 10 minutos, mesmo em arquitetura predominantemente assíncrona — necessária para evitar overselling em picos de demanda (alta temporada). Benchmark: operadores de passeio marítimo praticam holds de até 15 minutos.
4. **Prazo mínimo de compra:** o passeio não pode ser comprado com menos de 24h de antecedência da saída — janela alinhada ao benchmark de operadores marítimos brasileiros (24-48h) e à confiabilidade da previsão do tempo, que melhora perto da data (diferente da maré, que é determinística).
5. **Comissão da plataforma:** 12% sobre cada venda, aplicada via split no Pagar.me (proprietário recebe 88%). Valor configurável, dentro da faixa de 10-15% considerada padrão de mercado para marketplaces de baixo atrito.

## Itens em aberto (bloqueiam o `plan.md` até serem resolvidos)

Nenhum item de regra de negócio pendente.

## Review & Acceptance Checklist

- [x] Todos os itens `[NEEDS CLARIFICATION]` resolvidos
- [ ] Cenários de aceitação cobrem os principais fluxos do comprador
- [ ] Requisitos funcionais são testáveis independentemente de implementação
- [ ] Nenhum requisito contradiz os princípios da constitution
- [ ] Entidades-chave mapeáveis para o modelo de dados do DynamoDB (access patterns claros)
- [ ] Consistência confirmada com os eventos publicados por `spec-vessel-management.md`
