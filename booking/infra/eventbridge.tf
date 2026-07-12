# EventBridge Scheduler acionando a Lambda "sweeper" (ReleaseExpiredHoldsJob,
# T046) a cada 1 minuto — reforço de higiene para holds expirados (FR-004):
# o TTL nativo do DynamoDB só garante limpeza física em até 48h, então a
# aplicação sempre ignora holds vencidos na leitura independente deste job
# (ver dynamodb.tf) — este scheduler mantém o contador `held` do SEATCOUNT
# correto entre uma leitura e outra, não é a fonte de verdade de consistência.

resource "aws_scheduler_schedule" "hold_sweeper" {
  name       = "${var.project_name}-booking-hold-sweeper-${var.environment}"
  group_name = "default"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "rate(1 minute)"

  target {
    arn      = aws_lambda_function.sweeper.arn
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
  name               = "${var.project_name}-booking-sweeper-scheduler-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume_role.json
}

data "aws_iam_policy_document" "scheduler_invoke_lambda" {
  statement {
    actions   = ["lambda:InvokeFunction"]
    resources = [aws_lambda_function.sweeper.arn]
  }
}

resource "aws_iam_role_policy" "eventbridge_scheduler_invoke_lambda" {
  name   = "invoke-sweeper-lambda"
  role   = aws_iam_role.eventbridge_scheduler.id
  policy = data.aws_iam_policy_document.scheduler_invoke_lambda.json
}
