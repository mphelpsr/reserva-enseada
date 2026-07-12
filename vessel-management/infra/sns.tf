# Tópicos de eventos de domínio publicados pelo vessel-management (Princípio IV).
# Nomes de recurso usam hífen (SNS não aceita ponto); o nome "com ponto" usado nas
# specs/plans (ex.: vessel.availability.changed) é o nome lógico do evento, propagado
# via MessageAttribute "event-type" pelos publishers (Fase 3.4, T052-T055).
# Consumidos pelo módulo booking — ver specs/002-booking/plan.md.

resource "aws_sns_topic" "vessel_availability_changed" {
  name = "${var.project_name}-vessel-availability-changed-${var.environment}"
}

resource "aws_sns_topic" "vessel_seatlimit_changed" {
  name = "${var.project_name}-vessel-seatlimit-changed-${var.environment}"
}

resource "aws_sns_topic" "vessel_cancellation_operator_initiated" {
  name = "${var.project_name}-vessel-cancellation-operator-initiated-${var.environment}"
}

resource "aws_sns_topic" "vessel_transfer_viable" {
  name = "${var.project_name}-vessel-transfer-viable-${var.environment}"
}
