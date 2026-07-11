# Constitution — Sistema de Reservas de Passeios em Alto Mar

**Versão:** 1.3.0
**Data:** 2026-07-11 (última alteração: Princípio II passa a delimitar consistência aos canais controlados pela plataforma, com limite de vagas definido pelo proprietário)

## Propósito

Este documento define os princípios não-negociáveis que orientam todas as decisões de arquitetura, spec e implementação do sistema, composto por dois módulos principais: **Comprador de Tickets** e **Gestão de Embarcação**.

---

## Princípios Fundamentais

### I. A decisão final de disponibilidade pertence sempre ao proprietário
O sistema fornece dados de apoio — tábua de marés, previsão do tempo, e restrições operacionais que o próprio proprietário cadastra (ex.: escala de rodízio) — para ajudar na decisão de disponibilidade de cada dia. Esses dados NUNCA sobrepõem automaticamente a decisão do proprietário: condição desfavorável de maré/previsão gera um **alerta visível**, não um bloqueio. A única indisponibilidade imposta pelo sistema é uma restrição operacional que o próprio proprietário declarou (rodízio), e mesmo essa é escopada por tipo de passeio — pode bloquear Alto Mar sem bloquear Orla.

Toda spec que envolva disponibilidade deve tratar o cruzamento com maré/previsão como uma **camada de apoio à decisão**, nunca como fonte de verdade que substitui a escolha do proprietário.

**Justificativa:** quem responde legalmente e operacionalmente pela segurança do passeio é o proprietário — ele tem contexto (tipo de embarcação, experiência, rota) que o sistema não tem. Automatizar demais essa decisão tira dele o controle que é seu por direito e responsabilidade.

### II. Consistência de reserva acima de tudo — dentro do que a plataforma controla
Nenhuma vaga vendida pela plataforma pode ser vendida duas vezes dentro da própria plataforma. Operações que alterem disponibilidade (bloqueio, reserva, cancelamento, recálculo por previsão) devem ser tratadas como transações críticas, com estratégia explícita de concorrência (lock otimista, condição condicional no DynamoDB, ou fila serializada por embarcação/dia).

A plataforma NÃO tem visibilidade sobre vendas feitas pelo proprietário em canais externos não integrados. Para mitigar o risco de overbooking cruzado entre canais, o proprietário define quantas vagas por dia/tipo de passeio ficam disponíveis para venda na própria plataforma — esse número pode ser igual ou menor que a capacidade máxima da embarcação, nunca maior. A decisão de quanto reservar de capacidade para a plataforma é de risco e critério exclusivo do proprietário, assim como a gestão das vendas realizadas fora dela — a plataforma garante consistência apenas sobre o que ela mesma vende.

**Justificativa:** é uma plataforma financeira e de segurança — erro de disponibilidade gera prejuízo direto e risco físico ao cliente. Mas a plataforma não pode garantir o que não enxerga; a responsabilidade sobre canais externos precisa ficar explícita para não virar promessa que o sistema não consegue cumprir.

### III. Mobile-first para o comprador, desktop-first para a gestão — com visão mobile complementar para o proprietário
O módulo do comprador é desenhado prioritariamente para mobile (contexto de uso: decisão rápida, muitas vezes no dia do passeio). O módulo de gestão é desenhado prioritariamente para desktop — é onde vive a totalidade das funcionalidades de cadastro e operação (embarcações, disponibilidade, rodízio, cancelamentos). Complementarmente, o proprietário DEVE ter uma versão mobile "light", de caráter gerencial/dashboard, para acompanhamento e tomada de decisão rápida fora do desktop — não uma réplica das funcionalidades completas. Essa visão inclui, no mínimo: status das embarcações, volume de reservas e indicadores de condição do tempo relevantes para decisão imediata.

**Justificativa:** são personas e contextos de uso diferentes; otimizar os dois para o mesmo formato prejudica ambos. Mas o proprietário também precisa de visibilidade rápida quando não está no computador — sem isso ele fica cego a decisões urgentes (ex.: mudança de previsão) fora do horário de expediente.

### IV. Eventos como contrato entre módulos
Mudanças de estado relevantes (embarcação cadastrada, dia bloqueado/liberado, previsão atualizada, disponibilidade recalculada, reserva confirmada/cancelada) devem ser publicadas como eventos (SNS) e consumidas via fila (SQS) pelos módulos interessados. Módulos não devem se acoplar via chamada síncrona direta quando o evento resolver o caso.

**Justificativa:** os módulos evoluem em ritmos diferentes e a stack já prevê mensageria; acoplamento síncrono desnecessário mina essa escolha.

### V. Regras de negócio evoluem — a spec acompanha
As regras de maré/previsão, política de cancelamento e critérios de disponibilidade vão mudar com o tempo. Toda regra de negócio deve estar documentada na spec correspondente antes de virar código, e mudanças de regra exigem atualização da spec, não só do código.

**Justificativa:** é o motivo explícito de adotar spec-kit neste projeto.

### VI. Simplicidade antes de escalabilidade prematura
A arquitetura deve resolver o problema atual (poucas embarcações, poucos proprietários) sem fechar portas para escala futura. Decisões de particionamento, padrão de acesso do DynamoDB e desenho de filas devem ser justificadas pelo access pattern real, não por escala hipotética.

**Justificativa:** evitar overengineering em um produto que ainda está validando regras de negócio.

### VII. Cancelamento de reserva confirmada: transferência antes, reembolso garantido depois, sempre transparente ao comprador
Quando um imprevisto torna um dia com reservas confirmadas indisponível — indisponibilidade da própria embarcação, manutenção não programada, ou evento de força maior — o sistema DEVE, nessa ordem: (1) tentar viabilizar a transferência da reserva para outra embarcação do MESMO proprietário que tenha vaga disponível naquele dia; (2) se não houver embarcação do mesmo proprietário com vaga disponível, cancelar a reserva com reembolso integral automático e obrigatório ao comprador, seguindo as regras vigentes de cancelamento. Em qualquer um dos dois casos, o motivo real do imprevisto DEVE ser comunicado explicitamente ao comprador — nunca uma mensagem genérica. Este princípio vale tanto para o módulo de gestão de embarcação quanto para o módulo de booking (e para qualquer módulo futuro que gerencie reservas).

**Justificativa:** imprevistos operacionais são inevitáveis num negócio que depende de mar aberto; o que diferencia uma plataforma confiável é nunca deixar o comprador sem solução ou sem explicação. Limitar a transferência à mesma frota evita fricção comercial e operacional de mover cliente entre proprietários diferentes sem acordo prévio entre eles.

---

## Restrições Técnicas

- Backend: Java
- Mensageria: SQS (filas de processamento) + SNS (eventos/broadcast)
- Banco: DynamoDB (single-table design orientado a access patterns)
- Frontend: React — mobile-first (comprador) e desktop-first (gestão)
- Arquitetura: a definir por spec/plan, mas deve respeitar os princípios I e IV acima

## Fluxo de Trabalho

1. `constitution.md` (este documento) — não muda por feature, só por decisão deliberada de princípio.
2. `spec-<modulo>.md` — user stories, cenários e requisitos funcionais por módulo/feature.
3. `plan.md` — desenho técnico que implementa a spec respeitando esta constitution.
4. `tasks.md` — quebra executável do plano.

## Governança

- Esta constitution tem precedência sobre qualquer spec ou plan em caso de conflito.
- Alterações de princípio exigem incremento de versão (MAJOR se remove/redefine princípio, MINOR se adiciona, PATCH se é redação).
- Toda spec nova deve declarar explicitamente se depende de algum princípio acima.

**Versão:** 1.3.0 | **Ratificada em:** 2026-07-11 | **Última alteração:** 2026-07-11 (Princípio II revisado)
