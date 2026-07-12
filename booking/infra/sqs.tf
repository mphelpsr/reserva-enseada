# Fila SQS que consome os 4 tópicos SNS publicados pelo vessel-management
# (specs/001-vessel-management/tasks.md T052-T055): vessel.availability.changed,
# vessel.seatlimit.changed, vessel.cancellation.operator-initiated e
# vessel.transfer.viable. É suporte de infraestrutura para T049-T052
# (tasks.md) — o consumidor/lógica de aplicação entra só na Fase 3.4.
#
# Os tópicos do vessel-management são de OUTRO state Terraform (workspace
# próprio, ver vessel-management/infra/backend.tf) e precisam já existir
# quando este módulo é aplicado pela primeira vez — por isso a assinatura via
# SNS é condicionada a `var.vessel_management_topics_deployed`, para não
# travar o `terraform apply` deste módulo esperando por um recurso de outro
# state (mesma decisão espelhada em vessel-management/infra/sqs.tf para
# var.booking_topics_deployed).

variable "vessel_management_topics_deployed" {
  description = "Se true, assina a fila nos 4 tópicos SNS do vessel-management (precisam já existir no state daquele módulo)"
  type        = bool
  default     = false
}

variable "vessel_availability_changed_topic_name" {
  description = "Nome do tópico SNS vessel-availability-changed (módulo vessel-management, ver infra/sns.tf)"
  type        = string
  default     = "reserva-enseada-vessel-availability-changed-dev"
}

variable "vessel_seatlimit_changed_topic_name" {
  description = "Nome do tópico SNS vessel-seatlimit-changed (módulo vessel-management, ver infra/sns.tf)"
  type        = string
  default     = "reserva-enseada-vessel-seatlimit-changed-dev"
}

variable "vessel_cancellation_operator_initiated_topic_name" {
  description = "Nome do tópico SNS vessel-cancellation-operator-initiated (módulo vessel-management, ver infra/sns.tf)"
  type        = string
  default     = "reserva-enseada-vessel-cancellation-operator-initiated-dev"
}

variable "vessel_transfer_viable_topic_name" {
  description = "Nome do tópico SNS vessel-transfer-viable (módulo vessel-management, ver infra/sns.tf)"
  type        = string
  default     = "reserva-enseada-vessel-transfer-viable-dev"
}

resource "aws_sqs_queue" "operator_events_dlq" {
  name                      = "${var.project_name}-booking-operator-events-dlq-${var.environment}"
  message_retention_seconds = 1209600 # 14 dias
}

resource "aws_sqs_queue" "operator_events" {
  name                       = "${var.project_name}-booking-operator-events-${var.environment}"
  visibility_timeout_seconds = 60

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.operator_events_dlq.arn
    maxReceiveCount     = 5
  })
}

data "aws_sns_topic" "vessel_availability_changed" {
  count = var.vessel_management_topics_deployed ? 1 : 0
  name  = var.vessel_availability_changed_topic_name
}

data "aws_sns_topic" "vessel_seatlimit_changed" {
  count = var.vessel_management_topics_deployed ? 1 : 0
  name  = var.vessel_seatlimit_changed_topic_name
}

data "aws_sns_topic" "vessel_cancellation_operator_initiated" {
  count = var.vessel_management_topics_deployed ? 1 : 0
  name  = var.vessel_cancellation_operator_initiated_topic_name
}

data "aws_sns_topic" "vessel_transfer_viable" {
  count = var.vessel_management_topics_deployed ? 1 : 0
  name  = var.vessel_transfer_viable_topic_name
}

data "aws_iam_policy_document" "operator_events_queue_policy" {
  count = var.vessel_management_topics_deployed ? 1 : 0

  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.operator_events.arn]

    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values = [
        data.aws_sns_topic.vessel_availability_changed[0].arn,
        data.aws_sns_topic.vessel_seatlimit_changed[0].arn,
        data.aws_sns_topic.vessel_cancellation_operator_initiated[0].arn,
        data.aws_sns_topic.vessel_transfer_viable[0].arn,
      ]
    }
  }
}

resource "aws_sqs_queue_policy" "operator_events" {
  count     = var.vessel_management_topics_deployed ? 1 : 0
  queue_url = aws_sqs_queue.operator_events.id
  policy    = data.aws_iam_policy_document.operator_events_queue_policy[0].json
}

resource "aws_sns_topic_subscription" "vessel_availability_changed_to_queue" {
  count     = var.vessel_management_topics_deployed ? 1 : 0
  topic_arn = data.aws_sns_topic.vessel_availability_changed[0].arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.operator_events.arn
}

resource "aws_sns_topic_subscription" "vessel_seatlimit_changed_to_queue" {
  count     = var.vessel_management_topics_deployed ? 1 : 0
  topic_arn = data.aws_sns_topic.vessel_seatlimit_changed[0].arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.operator_events.arn
}

resource "aws_sns_topic_subscription" "vessel_cancellation_operator_initiated_to_queue" {
  count     = var.vessel_management_topics_deployed ? 1 : 0
  topic_arn = data.aws_sns_topic.vessel_cancellation_operator_initiated[0].arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.operator_events.arn
}

resource "aws_sns_topic_subscription" "vessel_transfer_viable_to_queue" {
  count     = var.vessel_management_topics_deployed ? 1 : 0
  topic_arn = data.aws_sns_topic.vessel_transfer_viable[0].arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.operator_events.arn
}
