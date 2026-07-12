# Tópicos de eventos de domínio publicados pelo booking (Princípio IV).
# Nomes de recurso usam hífen (SNS não aceita ponto); o nome "com ponto" usado
# nas specs/plans (ex.: booking.confirmed) é o nome lógico do evento, propagado
# via MessageAttribute "event-type" pelos publishers (Fase 3.4, T053-T055).
#
# Payload de cada evento fechado em 2026-07-12 — ver "Contrato da Saga" em
# plan.md e plan-vessel-management.md. Consumidos hoje pelo vessel-management
# (booking.confirmed via T059b já implementado; booking.cancelled/
# booking.transferred via T059, ainda pendente lá). Ficam disponíveis também
# para módulos futuros (relatórios, CRM) sem acoplar o booking a eles hoje —
# não há consumidor definido além do vessel-management, só o contrato
# publicado.

resource "aws_sns_topic" "booking_confirmed" {
  name = "${var.project_name}-booking-confirmed-${var.environment}"
}

resource "aws_sns_topic" "booking_cancelled" {
  name = "${var.project_name}-booking-cancelled-${var.environment}"
}

resource "aws_sns_topic" "booking_transferred" {
  name = "${var.project_name}-booking-transferred-${var.environment}"
}
