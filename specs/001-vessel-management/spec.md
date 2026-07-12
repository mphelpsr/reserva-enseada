# Spec — Módulo de Gestão de Embarcação

**Feature:** vessel-management
**Depende de (constitution):** Princípios I (decisão final é do proprietário), II (consistência), III (desktop-first), IV (eventos), VII (transferência antes de reembolso)
**Status:** rascunho

---

## User Scenarios & Testing

### Primary User Story
Como proprietário de embarcação, eu quero cadastrar minha(s) embarcação(ões) e indicar os dias em que estão disponíveis para passeio, para que compradores possam visualizar e reservar essas datas em tempo real, respeitando condições reais de maré e previsão do tempo.

### Acceptance Scenarios

1. **Given** um proprietário autenticado sem embarcações cadastradas,
   **When** ele cadastra uma nova embarcação com nome, capacidade máxima de passageiros e porto/local de saída,
   **Then** a embarcação passa a existir no sistema com status inicial `pendente_configuracao` até que pelo menos um dia de disponibilidade seja definido.

2. **Given** uma embarcação já cadastrada,
   **When** o proprietário marca um dia como "disponível" para um tipo de passeio específico (Alto Mar ou Orla),
   **Then** o sistema registra essa marcação como a disponibilidade final daquele dia/tipo de passeio e a expõe ao comprador — sem exigir aprovação ou validação de nenhuma condição externa.

3. **Given** um dia marcado como disponível pelo proprietário para Alto Mar,
   **When** a previsão do tempo ou tábua de maré indica condição desfavorável para aquele dia,
   **Then** o sistema exibe um **alerta** ao proprietário com as condições desfavoráveis, mas mantém o dia como disponível a menos que o proprietário decida alterá-lo manualmente. O alerta não bloqueia a venda.

3a. **Given** uma embarcação em dia de rodízio (cadastrado pelo próprio proprietário) que a impede de seguir para Alto Mar,
    **When** o sistema calcula a disponibilidade daquele dia,
    **Then** o passeio Alto Mar fica automaticamente indisponível para aquele dia, mas o passeio Orla permanece disponível para configuração normal do proprietário (sujeito às mesmas regras de alerta, não bloqueio, quanto a maré/previsão).

3b. **Given** um dia já cadastrado como rodízio para uma embarcação,
    **When** o proprietário tenta marcar Alto Mar como disponível nesse mesmo dia/embarcação,
    **Then** o sistema interrompe a ação e pergunta se ele deseja (a) mudar o dia da disponibilidade de Alto Mar, ou (b) alterar/remover o rodízio daquele dia — a marcação só é salva depois de uma escolha explícita, e o rodízio prevalece até que isso aconteça (ver FR-014).

4. **Given** um dia sem nenhuma reserva confirmada,
   **When** o proprietário decide marcar esse dia como indisponível (ex.: manutenção, feriado, uso pessoal da embarcação, ou clima por conta própria),
   **Then** o dia fica indisponível imediatamente, e o sistema publica evento de mudança de disponibilidade — sem impacto sobre reservas, pois não havia nenhuma.

5. **Given** um dia com reserva(s) confirmada(s),
   **When** o proprietário decide tornar esse dia indisponível (qualquer motivo, incluindo manutenção não programada ou força maior) ou entra em vigor um dia de rodízio para Alto Mar,
   **Then** o sistema impede a remoção direta e tenta primeiro viabilizar a transferência das reservas afetadas para outra embarcação do mesmo proprietário com vaga disponível; se nenhuma houver, publica um evento de cancelamento com reembolso integral automático, sempre acompanhado do motivo real a ser comunicado ao comprador (ver Princípio VII / FR-007).

6. **Given** uma embarcação cujo proprietário ainda não usou o padrão automático de vagas nenhuma ou uma vez,
   **When** o proprietário publica disponibilidade para um dia/tipo de passeio sem indicar a quantidade de vagas,
   **Then** o sistema aplica automaticamente 10% da capacidade máxima como limite de vagas, incrementa o contador da embarcação, e avisa o proprietário quantas aplicações automáticas já ocorreram.

6a. **Given** uma embarcação cujo contador de padrão automático já atingiu 2,
    **When** o proprietário publica disponibilidade para um dia/tipo de passeio sem indicar a quantidade de vagas,
    **Then** o sistema NÃO aplica nenhum padrão automático — o dia/tipo de passeio fica com zero vagas disponíveis para venda até que o proprietário defina um valor explícito.

7. **Given** um proprietário sem `payment_recebedor_id` cadastrado,
   **When** ele tenta marcar uma embarcação como `ativa`,
   **Then** o sistema recusa a ativação e orienta o proprietário a completar o cadastro da subconta de pagamento primeiro (ver FR-016).

### Edge Cases

- Proprietário com múltiplas embarcações: cada embarcação tem calendário de disponibilidade independente.
- Dois proprietários tentando cadastrar embarcação com o mesmo nome/documento — resolvido via identificador único (FR-009).
- Proprietário marca Alto Mar como disponível em um dia de rodízio cadastrado por ele mesmo para essa mesma embarcação — **resolvido (ver FR-014)**: o dia de rodízio sempre prevalece; o sistema questiona o proprietário e não permite os dois estados coexistindo sem decisão explícita.
- Proprietário desativa a conta/embarcação com reservas futuras confirmadas — ver FR-002 (exige transferência).
- Cliente reserva Orla em dia de rodízio e, posteriormente, o rodízio é alterado/removido pelo proprietário — não afeta a reserva de Orla já confirmada, pois rodízio nunca bloqueou esse tipo de passeio.

---

## Requirements

### Functional Requirements

- **FR-001**: O sistema DEVE permitir que um proprietário cadastre uma ou mais embarcações, com no mínimo: nome, capacidade máxima de passageiros, porto/local de saída.
- **FR-002**: O sistema DEVE permitir que o proprietário edite dados cadastrais de uma embarcação. A remoção de uma embarcação com reservas futuras confirmadas NÃO é permitida diretamente — o proprietário DEVE primeiro transferir as reservas futuras para outra embarcação compatível (mesma capacidade mínima e porto de saída) antes que a remoção seja concluída. Sem reservas futuras pendentes, a remoção é direta.
- **FR-003**: O sistema DEVE permitir que o proprietário marque e desmarque, por dia e por tipo de passeio (Alto Mar / Orla), a disponibilidade de cada embarcação. Essa marcação É a disponibilidade final exposta ao comprador — não depende de validação externa.
- **FR-004**: O sistema DEVE permitir que o proprietário marque um dia como indisponível a qualquer momento e por qualquer motivo (motivo opcional para registro: manutenção, clima por conta própria, indisponibilidade pessoal etc.), com efeito imediato sobre a disponibilidade exposta ao comprador.
- **FR-005**: O sistema DEVE publicar um evento (SNS) sempre que a disponibilidade declarada de uma embarcação for alterada (marcação, desmarcação, ou mudança de tipo de passeio), para consumo pelo módulo de booking.
- **FR-006**: O sistema DEVE calcular e exibir ao proprietário um **indicador de apoio** (favorável/desfavorável) cruzando tábua de maré e previsão do tempo para cada dia, funcionando como alerta — este indicador NUNCA altera automaticamente a disponibilidade declarada pelo proprietário (ver Princípio I).
- **FR-007**: O sistema DEVE impedir a remoção/bloqueio direto de um dia que já possua reserva confirmada. Quando o proprietário decide tornar indisponível um dia que já tem reserva confirmada — por qualquer motivo, incluindo os casos de FR-004, indisponibilidade da própria embarcação, manutenção não programada, ou força maior — o sistema DEVE seguir o fluxo do Princípio VII: (1) tentar viabilizar a transferência da(s) reserva(s) para outra embarcação do MESMO proprietário que tenha vaga disponível naquele dia; (2) se nenhuma embarcação do mesmo proprietário tiver vaga disponível, publicar um evento de cancelamento com reembolso integral automático e obrigatório, sem janela de análise. Em ambos os casos, o motivo real da mudança DEVE ser comunicado explicitamente ao comprador (nunca uma mensagem genérica) — este requisito de comunicação é executado pelo módulo de booking, mas a origem/motivo estruturado DEVE ser fornecido por este módulo no evento publicado. Cancelamentos por iniciativa do próprio cliente (desistência) seguem regra distinta, incluindo o direito de arrependimento do Art. 49 do CDC (7 dias corridos da compra, reembolso integral) e uma política em camadas para desistência fora dessa janela — ambas definidas e executadas no módulo de booking, não neste.
- **FR-008**: O sistema DEVE notificar o proprietário (alerta, não bloqueio) sempre que a condição de maré/previsão for desfavorável para um dia já marcado como disponível, permitindo que ele decida se mantém ou altera a marcação.
- **FR-009**: O sistema DEVE registrar como identificador único de embarcação a combinação de: número de registro na Capitania dos Portos + CPF/CNPJ do proprietário + nome legal da embarcação. Adicionalmente, DEVE existir um campo de nome fantasia/receptivo (ex.: nome do restaurante, pousada ou operador que serve de referência de localização), exibido ao comprador para facilitar a identificação do ponto de saída — esse campo é distinto do nome legal usado na identificação única.
- **FR-010**: O sistema DEVE suportar múltiplas embarcações por proprietário, cada uma com calendário de disponibilidade independente.
- **FR-011**: A capacidade máxima de passageiros é um atributo fixo da embarcação (não varia por tipo de passeio ou configuração do dia).
- **FR-012**: O sistema DEVE suportar dois tipos de passeio distintos por embarcação — **Alto Mar** (navegação até as galés) e **Orla** (navegação costeira) — com disponibilidade configurada de forma independente por dia.
- **FR-013**: O sistema DEVE permitir que o proprietário cadastre manualmente uma escala de rodízio por embarcação (dias em que ela não pode seguir para Alto Mar). Nos dias de rodízio, o sistema DEVE bloquear automaticamente apenas a disponibilidade de Alto Mar — a disponibilidade de Orla permanece sob controle normal do proprietário, sujeita apenas aos alertas de FR-006/FR-008. Esta é a única restrição operacional que o sistema impõe automaticamente sobre a disponibilidade.
- **FR-014**: O dia de rodízio cadastrado SEMPRE prevalece sobre uma tentativa de marcar Alto Mar como disponível no mesmo dia/embarcação. Se o proprietário tentar marcar Alto Mar disponível em um dia já cadastrado como rodízio, o sistema NÃO DEVE permitir a coexistência silenciosa dos dois estados — DEVE interromper a ação com uma pergunta explícita, oferecendo duas saídas: (a) alterar o dia da disponibilidade de Alto Mar para outra data, ou (b) alterar/remover o dia de rodízio daquela embarcação. A ação só é concluída após o proprietário escolher uma das duas opções.
- **FR-015**: O sistema DEVE permitir que o proprietário defina, por dia e por tipo de passeio, a quantidade de vagas disponibilizadas para venda na plataforma — valor que DEVE ser menor ou igual à capacidade máxima da embarcação (FR-011), nunca maior. Se o proprietário não indicar essa quantidade, o sistema DEVE aplicar automaticamente um valor padrão de 10% da capacidade máxima da embarcação (arredondado para baixo, mínimo de 1 vaga) — essa aplicação automática do padrão é permitida no máximo **2 vezes por embarcação** (contador cumulativo, não reinicia por dia). Da 3ª vez em diante em que o proprietário deixar de indicar a quantidade de vagas, o sistema NÃO DEVE disponibilizar nenhuma vaga para venda na plataforma naquele dia/tipo de passeio (zero vagas), até que o proprietário defina explicitamente um valor. O sistema DEVE exibir ao proprietário quantas vezes o padrão automático já foi aplicado, para deixar claro que o controle e o risco da disponibilidade de vagas são dele — dado o impacto direto na operação e no cliente (ver Princípio II).
- **FR-016**: O sistema DEVE registrar um `payment_recebedor_id` no cadastro do proprietário — identificador da subconta/recebedor no gateway de pagamento (Pagar.me), necessário para viabilizar o split de pagamento (repasse automático do valor líquido ao proprietário e da comissão à plataforma a cada venda, conforme exigido pela Circular 3.815/2016 do Banco Central para marketplaces). Uma embarcação NÃO DEVE poder ser marcada como `ativa` (FR-001) sem que o proprietário correspondente tenha um `payment_recebedor_id` válido cadastrado — sem isso, não há como repassar o valor de nenhuma venda feita na plataforma para aquele proprietário.

  > **Nota de 2026-07-12 — revisão de modelo planejada, ainda NÃO implementada:** decidido nesta data que o proprietário NÃO deve ser obrigado a ter cadastro próprio no gateway de pagamento — o modelo pretendido é a plataforma receber 100% numa conta única (CNPJ da própria plataforma) e repassar o valor líquido ao proprietário por fora do Pagar.me (mecanismo de repasse ainda por desenhar). Isso muda o propósito de `payment_recebedor_id`: passaria a ser um identificador da plataforma, não do proprietário individual, e o gate deste FR-016 (bloquear `ativa` sem subconta do proprietário) deixaria de fazer sentido. **Este FR ainda descreve o comportamento ATUAL, realmente implementado** (T019/`PaymentRecebedorGateIntegrationTest`) — a mudança não foi codificada, só registrada aqui como decisão de produto pendente de desenho. Ver também nota equivalente em `spec-booking.md` (FR-015) e a atualização de escopo em `plan.md` (T059c). Ao retomar isso: revisar se o modelo de repasse por fora do gateway ainda atende à exigência regulatória citada acima (Circular 3.815/2016) antes de remover o split automático do Pagar.me.

### Key Entities

- **Proprietário (Owner)**: pessoa física ou jurídica dona de uma ou mais embarcações. Atributos-chave: identificação, contato, embarcações vinculadas, `payment_recebedor_id` (subconta no gateway de pagamento, obrigatório para ativar qualquer embarcação — ver FR-016).
- **Embarcação (Vessel)**: unidade que realiza o passeio. Atributos-chave: nome legal, nome fantasia/receptivo (exibido ao comprador), identificador único (nº registro Capitania dos Portos + CPF/CNPJ do proprietário + nome legal), capacidade máxima (fixa), porto de saída, status (`pendente_configuracao`, `ativa`, `inativa`), proprietário.
- **Transferência de Embarcação (VesselTransfer)**: operação obrigatória antes da remoção de uma embarcação com reservas futuras confirmadas — move as reservas para outra embarcação compatível (mesma capacidade mínima e porto de saída).
- **Tentativa de Transferência de Reserva (BookingTransferAttempt)**: passo obrigatório antes de qualquer cancelamento com reembolso — verifica se existe outra embarcação do MESMO proprietário com vaga disponível naquele dia antes de acionar o reembolso (ver Princípio VII). Não busca embarcações de outros proprietários.
- **Evento de Cancelamento por Força Maior/Operador (OperatorInitiatedCancellation)**: evento publicado quando a transferência (BookingTransferAttempt) não é possível e uma reserva confirmada precisa ser cancelada por causa não atribuível ao cliente (indisponibilidade da embarcação, manutenção não programada, força maior, condição de maré/previsão por decisão do proprietário). Inclui o motivo estruturado a ser comunicado ao comprador e dispara reembolso integral automático no módulo de pagamento/booking.
- **Disponibilidade Declarada (DeclaredAvailability)**: registro do proprietário indicando disponibilidade de um dia específico, por embarcação e por tipo de passeio (Alto Mar/Orla). É a disponibilidade final exposta ao comprador — o proprietário tem controle total sobre esse dado (ver Princípio I).
- **Indicador de Maré/Previsão (WeatherTideAdvisory)**: dado de apoio calculado (favorável/desfavorável) por dia e embarcação, exibido como alerta ao proprietário. Não altera a Disponibilidade Declarada.
- **Tipo de Passeio (TourType)**: `alto_mar` (navegação até as galés) ou `orla` (navegação costeira). Disponibilidade é configurada de forma independente por tipo.
- **Escala de Rodízio (RotationSchedule)**: cadastro manual do proprietário indicando dias em que a embarcação não pode seguir para Alto Mar. Bloqueia automaticamente apenas o tipo de passeio `alto_mar` naqueles dias; `orla` não é afetado.
- **Limite de Vagas na Plataforma (PlatformSeatLimit)**: quantidade de vagas por dia/tipo de passeio que o proprietário decide disponibilizar para venda na plataforma, sempre ≤ capacidade máxima da embarcação. Reflete o risco de vendas simultâneas em canais externos não integrados (ver Princípio II) — decisão e responsabilidade exclusivas do proprietário.
- **Contador de Padrão Automático (DefaultSeatUsageCounter)**: contador cumulativo por embarcação de quantas vezes o sistema aplicou o valor padrão de 10% da capacidade máxima por ausência de indicação do proprietário (FR-015). Ao atingir 2, a próxima ausência de indicação resulta em zero vagas disponíveis, não em novo padrão automático.
- **Evento de Disponibilidade Alterada**: mensagem publicada via SNS toda vez que FR-003, FR-004 ou FR-013 alteram a disponibilidade final, consumida pelo módulo de booking.

---

## Decisões registradas (resolvidas em 2026-07-11)

1. **Reembolso por cancelamento de dia já reservado (não-cliente):** integral e automático, sempre — condição de maré/previsão desfavorável ou bloqueio manual do proprietário (benchmark GetYourGuide: força maior e cancelamento por operadora = reembolso integral automático). Cancelamento por desistência do cliente segue regra distinta no módulo de booking, incluindo direito de arrependimento (Art. 49 CDC, 7 dias corridos).
2. **Identificador único de embarcação:** nº de registro na Capitania dos Portos + CPF/CNPJ do proprietário + nome legal. Nome fantasia/receptivo é campo separado, voltado à identificação do ponto de saída pelo comprador.
3. **Capacidade máxima:** fixa por embarcação.
4. **Remoção de embarcação com reservas futuras:** exige transferência prévia das reservas para outra embarcação compatível.
5. **Modelo de disponibilidade (ajuste):** o cruzamento de maré/previsão é apoio (alerta), não bloqueio — a decisão final é sempre do proprietário. A única indisponibilidade imposta automaticamente pelo sistema é o rodízio (cadastrado manualmente pelo próprio proprietário), e mesmo essa afeta apenas o tipo de passeio Alto Mar, nunca Orla. Isso altera o Princípio I da constitution.
6. **Cancelamento de dia com reservas confirmadas:** o sistema sempre tenta transferência para outra embarcação do MESMO proprietário com vaga disponível primeiro; só cancela com reembolso integral automático se não houver embarcação própria disponível naquele dia. Aplica-se a indisponibilidade da própria embarcação, manutenção não programada e força maior. O motivo real deve ser explicitado ao comprador em qualquer um dos casos. Isso virou o Princípio VII da constitution, valendo também para o módulo de booking.
7. **Visão mobile do proprietário:** além do desktop completo, o proprietário terá uma versão mobile "light" de acompanhamento gerencial — status das embarcações, volume de reservas e indicadores de tempo relevantes para decisão rápida. Escopo detalhado dessa visão fica para uma spec própria (`spec-vessel-management-mobile-dashboard`), fora do escopo funcional detalhado deste documento; registrado aqui porque altera o Princípio III da constitution.
8. **Limite de vagas vendidas na plataforma:** o proprietário define quantas vagas por dia/tipo de passeio ficam disponíveis para venda na plataforma (≤ capacidade máxima), para mitigar overbooking com canais externos não integrados. Se ele não indicar, o sistema aplica 10% da capacidade máxima como padrão — no máximo 2 vezes por embarcação (contador cumulativo). Da 3ª ausência de indicação em diante, o sistema não disponibiliza vagas até definição explícita. Risco e gestão de vendas fora da plataforma são de responsabilidade exclusiva do proprietário. Isso revisou o Princípio II da constitution, delimitando a garantia de consistência aos canais controlados pela própria plataforma.
9. **Split de pagamento (dependência do módulo booking):** o gateway escolhido para o booking (Pagar.me) exige que cada proprietário tenha uma subconta/recebedor cadastrada para receber o repasse automático das vendas. Por isso, o cadastro do proprietário ganhou o campo `payment_recebedor_id` (FR-016), e uma embarcação não pode ficar `ativa` sem que o proprietário correspondente tenha esse identificador válido.

## Review & Acceptance Checklist

- [x] Todos os itens `[NEEDS CLARIFICATION]` resolvidos
- [ ] Cenários de aceitação cobrem os principais fluxos do proprietário
- [ ] Requisitos funcionais são testáveis independentemente de implementação
- [ ] Nenhum requisito contradiz os princípios da constitution
- [ ] Entidades-chave mapeáveis para o modelo de dados do DynamoDB (access patterns claros)
