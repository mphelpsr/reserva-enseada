# Fila SQS que fecha o último passo da Saga coreografada de cancelamento
# (Princípio VII) E mantém a réplica local ConfirmedBookingCount (decisão de
# 2026-07-12, plan.md — "Eventos Consumidos"): consome `booking.transferred`,
# `booking.cancelled` e `booking.confirmed`, publicados pelo módulo booking
# (specs/002-booking/tasks.md, T053-T055). É suporte de infraestrutura para
# T059/T059b (tasks.md) — o consumidor/lógica de aplicação entra só na Fase
# 3.4, depois que o booking tiver implementado T053/T055 e o payload de cada
# evento estiver acordado entre os dois lados.
#
# Os tópicos do booking são de OUTRO state Terraform (workspace próprio do
# booking, ver specs/002-booking/tasks.md T001) e ainda não existem quando este
# módulo é aplicado pela primeira vez — por isso a assinatura via SNS é
# condicionada a `var.booking_topics_deployed`, para não travar o
# `terraform apply` deste módulo esperando por um recurso de outro state.
# Ativar depois que specs/002-booking T053-T055 estiverem em produção.

variable "booking_topics_deployed" {
  description = "Se true, assina a fila nos tópicos SNS booking.cancelled/booking.transferred/booking.confirmed (precisam já existir no state do módulo booking)"
  type        = bool
  default     = false
}

variable "booking_cancelled_topic_name" {
  description = "Nome do tópico SNS booking-cancelled (módulo booking, ver specs/002-booking/infra/sns.tf)"
  type        = string
  default     = "reserva-enseada-booking-cancelled-dev"
}

variable "booking_transferred_topic_name" {
  description = "Nome do tópico SNS booking-transferred (módulo booking, ver specs/002-booking/infra/sns.tf)"
  type        = string
  default     = "reserva-enseada-booking-transferred-dev"
}

variable "booking_confirmed_topic_name" {
  description = "Nome do tópico SNS booking-confirmed (módulo booking, ver specs/002-booking/infra/sns.tf) — alimenta a réplica local ConfirmedBookingCount (T059b)"
  type        = string
  default     = "reserva-enseada-booking-confirmed-dev"
}

resource "aws_sqs_queue" "booking_events_dlq" {
  name                      = "${var.project_name}-booking-events-dlq-${var.environment}"
  message_retention_seconds = 1209600 # 14 dias
}

resource "aws_sqs_queue" "booking_events" {
  name                       = "${var.project_name}-booking-events-${var.environment}"
  visibility_timeout_seconds = 60

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.booking_events_dlq.arn
    maxReceiveCount     = 5
  })
}

data "aws_sns_topic" "booking_cancelled" {
  count = var.booking_topics_deployed ? 1 : 0
  name  = var.booking_cancelled_topic_name
}

data "aws_sns_topic" "booking_transferred" {
  count = var.booking_topics_deployed ? 1 : 0
  name  = var.booking_transferred_topic_name
}

data "aws_sns_topic" "booking_confirmed" {
  count = var.booking_topics_deployed ? 1 : 0
  name  = var.booking_confirmed_topic_name
}

data "aws_iam_policy_document" "booking_events_queue_policy" {
  count = var.booking_topics_deployed ? 1 : 0

  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.booking_events.arn]

    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values = [
        data.aws_sns_topic.booking_cancelled[0].arn,
        data.aws_sns_topic.booking_transferred[0].arn,
        data.aws_sns_topic.booking_confirmed[0].arn,
      ]
    }
  }
}

resource "aws_sqs_queue_policy" "booking_events" {
  count     = var.booking_topics_deployed ? 1 : 0
  queue_url = aws_sqs_queue.booking_events.id
  policy    = data.aws_iam_policy_document.booking_events_queue_policy[0].json
}

resource "aws_sns_topic_subscription" "booking_cancelled_to_queue" {
  count     = var.booking_topics_deployed ? 1 : 0
  topic_arn = data.aws_sns_topic.booking_cancelled[0].arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.booking_events.arn
}

resource "aws_sns_topic_subscription" "booking_transferred_to_queue" {
  count     = var.booking_topics_deployed ? 1 : 0
  topic_arn = data.aws_sns_topic.booking_transferred[0].arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.booking_events.arn
}

resource "aws_sns_topic_subscription" "booking_confirmed_to_queue" {
  count     = var.booking_topics_deployed ? 1 : 0
  topic_arn = data.aws_sns_topic.booking_confirmed[0].arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.booking_events.arn
}
