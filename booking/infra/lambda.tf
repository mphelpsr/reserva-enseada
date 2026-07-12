# Lambdas de compute do módulo + IAM com permissões mínimas por função, desde
# já seguindo o padrão de least-privilege por Lambda (não uma role única
# compartilhada) — mesma lição aplicada em vessel-management/infra/lambda.tf
# (T062 lá), aplicada aqui desde o início em vez de corrigida depois.
#
# Diferente do vessel-management (SnapStart), a estratégia de cold start aqui
# é *provisioned concurrency* opcional nos endpoints de hold/checkout — ver
# plan.md "Justificativa da escolha de compute" e T010 (var.enable_provisioned_concurrency
# abaixo, off por padrão, não bloqueia o deploy inicial).
#
# O artefato (`var.lambda_artifact_path`) é o uber-jar gerado por
# `mvn package` (classifier "aws", ver ../pom.xml) — precisa existir localmente
# antes de `terraform apply`.

data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

locals {
  dynamodb_table_arn = aws_dynamodb_table.booking.arn
  dynamodb_gsi1_arn  = "${aws_dynamodb_table.booking.arn}/index/GSI1"
}

# ---------------------------------------------------------------------------
# api — BookingController/CalendarController (T047-T048). Atende hold/confirm/
# cancel/respond-transfer/histórico. Único que precisa de acesso de escrita ao
# Pagar.me (fora do IAM, é chamada HTTPS de saída) e publica booking.confirmed
# (T053) e booking.cancelled (T054, quando disparado por desistência via T040).
# ---------------------------------------------------------------------------

resource "aws_iam_role" "api_lambda" {
  name               = "${var.project_name}-booking-api-lambda-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "api_lambda_basic_execution" {
  role       = aws_iam_role.api_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "api_lambda_dynamodb" {
  name = "dynamodb"
  role = aws_iam_role.api_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:Query",
        "dynamodb:TransactWriteItems",
      ]
      Resource = [local.dynamodb_table_arn, local.dynamodb_gsi1_arn]
    }]
  })
}

resource "aws_iam_role_policy" "api_lambda_sns" {
  name = "sns-publish"
  role = aws_iam_role.api_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["sns:Publish"]
      Resource = [
        aws_sns_topic.booking_confirmed.arn,
        aws_sns_topic.booking_cancelled.arn,
        aws_sns_topic.booking_transferred.arn,
      ]
    }]
  })
}

resource "aws_iam_role_policy" "api_lambda_ses" {
  name = "ses-send"
  role = aws_iam_role.api_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ses:SendEmail", "ses:SendTemplatedEmail"]
      Resource = "*"
    }]
  })
}

resource "aws_lambda_function" "api" {
  function_name = "${var.project_name}-booking-api-${var.environment}"
  role          = aws_iam_role.api_lambda.arn
  handler       = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
  runtime       = "java21"
  architectures = ["x86_64"]

  filename         = var.lambda_artifact_path
  source_code_hash = filebase64sha256(var.lambda_artifact_path)

  memory_size = 1024
  timeout     = 29 # limite máximo de integração do API Gateway HTTP API

  environment {
    variables = {
      SPRING_CLOUD_FUNCTION_DEFINITION = "apiHandler"
      DYNAMODB_TABLE_NAME              = aws_dynamodb_table.booking.name
      SNS_BOOKING_CONFIRMED_ARN        = aws_sns_topic.booking_confirmed.arn
      SNS_BOOKING_CANCELLED_ARN        = aws_sns_topic.booking_cancelled.arn
      SNS_BOOKING_TRANSFERRED_ARN      = aws_sns_topic.booking_transferred.arn
      PAGARME_API_KEY                  = var.pagarme_api_key
      PLATFORM_COMMISSION_PERCENTAGE   = var.platform_commission_percentage
      SES_SOURCE_EMAIL                 = var.ses_source_email
      SES_BOOKING_CONFIRMED_TEMPLATE   = aws_ses_template.booking_confirmed.name
      SES_BOOKING_CANCELLED_TEMPLATE   = aws_ses_template.booking_cancelled.name
      SES_BOOKING_TRANSFERRED_TEMPLATE = aws_ses_template.booking_transferred.name
    }
  }

  publish = true
}

# ---------------------------------------------------------------------------
# operator_events_consumer — T049-T052, acionada pela fila SQS
# operator_events (sqs.tf, T005). Consome os 4 tópicos do vessel-management;
# a resposta a vessel.cancellation.operator-initiated (T042) publica
# booking.cancelled (T054), e a resposta a vessel.transfer.viable, quando o
# comprador aceita (T041/T055), publica booking.transferred — por isso
# também precisa de sns:Publish.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "operator_events_consumer_lambda" {
  name               = "${var.project_name}-booking-operator-events-lambda-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "operator_events_consumer_basic_execution" {
  role       = aws_iam_role.operator_events_consumer_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "operator_events_consumer_dynamodb" {
  name = "dynamodb"
  role = aws_iam_role.operator_events_consumer_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:Query",
      ]
      Resource = [local.dynamodb_table_arn, local.dynamodb_gsi1_arn]
    }]
  })
}

resource "aws_iam_role_policy" "operator_events_consumer_sqs" {
  name = "sqs"
  role = aws_iam_role.operator_events_consumer_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
      ]
      Resource = aws_sqs_queue.operator_events.arn
    }]
  })
}

resource "aws_iam_role_policy" "operator_events_consumer_sns" {
  name = "sns-publish"
  role = aws_iam_role.operator_events_consumer_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["sns:Publish"]
      Resource = [
        aws_sns_topic.booking_cancelled.arn,
        aws_sns_topic.booking_transferred.arn,
      ]
    }]
  })
}

resource "aws_iam_role_policy" "operator_events_consumer_ses" {
  name = "ses-send"
  role = aws_iam_role.operator_events_consumer_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ses:SendEmail", "ses:SendTemplatedEmail"]
      Resource = "*"
    }]
  })
}

resource "aws_lambda_function" "operator_events_consumer" {
  function_name = "${var.project_name}-booking-operator-events-${var.environment}"
  role          = aws_iam_role.operator_events_consumer_lambda.arn
  handler       = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
  runtime       = "java21"
  architectures = ["x86_64"]

  filename         = var.lambda_artifact_path
  source_code_hash = filebase64sha256(var.lambda_artifact_path)

  memory_size = 512
  timeout     = 60

  environment {
    variables = {
      SPRING_CLOUD_FUNCTION_DEFINITION = "operatorEventsConsumer"
      DYNAMODB_TABLE_NAME              = aws_dynamodb_table.booking.name
      SNS_BOOKING_CANCELLED_ARN        = aws_sns_topic.booking_cancelled.arn
      SNS_BOOKING_TRANSFERRED_ARN      = aws_sns_topic.booking_transferred.arn
      SES_SOURCE_EMAIL                 = var.ses_source_email
      SES_BOOKING_CANCELLED_TEMPLATE   = aws_ses_template.booking_cancelled.name
    }
  }
}

resource "aws_lambda_event_source_mapping" "operator_events_to_consumer" {
  event_source_arn = aws_sqs_queue.operator_events.arn
  function_name    = aws_lambda_function.operator_events_consumer.arn
  batch_size       = 10
}

# ---------------------------------------------------------------------------
# sweeper — ReleaseExpiredHoldsJob (T046), acionada pelo EventBridge
# Scheduler a cada 1 min (eventbridge.tf, T007). Só decrementa `held` e
# remove o HOLD expirado — nunca toca em BOOKING/SEATCOUNT.sold.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "sweeper_lambda" {
  name               = "${var.project_name}-booking-sweeper-lambda-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "sweeper_lambda_basic_execution" {
  role       = aws_iam_role.sweeper_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "sweeper_lambda_dynamodb" {
  name = "dynamodb"
  role = aws_iam_role.sweeper_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:Scan",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
      ]
      Resource = [local.dynamodb_table_arn]
    }]
  })
}

# Ofertas de transferência vencidas sem resposta em 48h (T046, segunda
# responsabilidade do sweeper) publicam booking.cancelled para fechar a Saga
# do lado vessel-management — sem isto a mensagem seria silenciosamente
# pulada em produção (mesma resiliência de SnsEventListener quando o ARN não
# está configurado), nunca chegando ao outro módulo.
resource "aws_iam_role_policy" "sweeper_lambda_sns" {
  name = "sns-publish"
  role = aws_iam_role.sweeper_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sns:Publish"]
      Resource = [aws_sns_topic.booking_cancelled.arn]
    }]
  })
}

resource "aws_lambda_function" "sweeper" {
  function_name = "${var.project_name}-booking-sweeper-${var.environment}"
  role          = aws_iam_role.sweeper_lambda.arn
  handler       = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
  runtime       = "java21"
  architectures = ["x86_64"]

  filename         = var.lambda_artifact_path
  source_code_hash = filebase64sha256(var.lambda_artifact_path)

  memory_size = 512
  timeout     = 60

  environment {
    variables = {
      SPRING_CLOUD_FUNCTION_DEFINITION = "releaseExpiredHoldsJob"
      DYNAMODB_TABLE_NAME              = aws_dynamodb_table.booking.name
      SNS_BOOKING_CANCELLED_ARN        = aws_sns_topic.booking_cancelled.arn
    }
  }
}

# ---------------------------------------------------------------------------
# T010 — provisioned concurrency opcional para o endpoint de maior sensibilidade
# a cold start (hold/checkout). Off por padrão (var.enable_provisioned_concurrency),
# ativação manual sob demanda em janelas de alta temporada conhecidas — não
# bloqueia o deploy inicial (plan.md, "Justificativa da escolha de compute").
# ---------------------------------------------------------------------------

variable "enable_provisioned_concurrency" {
  description = "Ativa provisioned concurrency na Lambda api (T010) — ligar manualmente em janelas de alta demanda conhecidas, não no deploy padrão"
  type        = bool
  default     = false
}

variable "provisioned_concurrency_count" {
  description = "Quantidade de execuções provisionadas quando var.enable_provisioned_concurrency = true"
  type        = number
  default     = 2
}

resource "aws_lambda_provisioned_concurrency_config" "api" {
  count                             = var.enable_provisioned_concurrency ? 1 : 0
  function_name                     = aws_lambda_function.api.function_name
  qualifier                         = aws_lambda_function.api.version
  provisioned_concurrent_executions = var.provisioned_concurrency_count
}
