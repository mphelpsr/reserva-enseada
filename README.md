# Sistema de Reservas de Passeios em Alto Mar

Plataforma de reserva de passeios marítimos, com dois módulos principais:

- **vessel-management** — cadastro de embarcações, disponibilidade, rodízio e limite de vagas (visão do proprietário).
- **booking** — reserva e pagamento de passeios (visão do comprador).

Construído seguindo [spec-kit](https://github.com/github/spec-kit): a `constitution.md` define os princípios de negócio que atravessam todos os módulos; cada feature em `specs/` tem sua própria `spec.md` (requisitos), `plan.md` (arquitetura técnica) e `tasks.md` (tarefas executáveis).

## Estrutura

```
.specify/memory/constitution.md   # Princípios do projeto (versionado, v1.3.0)
specs/001-vessel-management/      # spec, plan e tasks do módulo de gestão de embarcação
specs/002-booking/                 # spec, plan e tasks do módulo de reserva/comprador
```

## Stack técnica

- Backend: Java 21 / Spring Boot 3.x
- Persistência: DynamoDB (single-table design por módulo)
- Mensageria: SNS + SQS (comunicação entre módulos via eventos, nunca chamada síncrona)
- Compute: AWS Lambda (SnapStart)
- IaC: Terraform
- Frontend: React (mobile-first no booking, desktop-first no vessel-management)

## Status

Ambos os módulos têm `spec.md`, `plan.md` e `tasks.md` completos, sem itens de negócio pendentes. Os dois módulos se comunicam via uma Saga coreografada (ver Princípio VII da constitution) para o fluxo de cancelamento/transferência de reservas.
