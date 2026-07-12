# EventBridge Scheduler acionando a Lambda "advisory_job" para recalcular o
# WeatherTideAdvisory (FR-006) periodicamente. Frequência não é definida na
# spec/plan — 6h é um ponto de partida razoável para dados de maré/previsão,
# ajustável via var.advisory_recalculation_rate sem mudança de código.

variable "advisory_recalculation_rate" {
  description = "Expressão de agendamento (rate/cron) do recálculo de advisory (FR-006)"
  type        = string
  default     = "rate(6 hours)"
}

resource "aws_scheduler_schedule" "advisory_recalculation" {
  name       = "${var.project_name}-advisory-recalculation-${var.environment}"
  group_name = "default"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = var.advisory_recalculation_rate

  target {
    arn      = aws_lambda_function.advisory_job.arn
    role_arn = aws_iam_role.eventbridge_scheduler.arn
  }
}

data "aws_iam_policy_document" "scheduler_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eventbridge_scheduler" {
  name               = "${var.project_name}-advisory-scheduler-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume_role.json
}

data "aws_iam_policy_document" "scheduler_invoke_lambda" {
  statement {
    actions   = ["lambda:InvokeFunction"]
    resources = [aws_lambda_function.advisory_job.arn]
  }
}

resource "aws_iam_role_policy" "eventbridge_scheduler_invoke_lambda" {
  name   = "invoke-advisory-lambda"
  role   = aws_iam_role.eventbridge_scheduler.id
  policy = data.aws_iam_policy_document.scheduler_invoke_lambda.json
}
