# Lambdas de compute do módulo + IAM com permissões mínimas por função (T062):
# cada Lambda tem sua PRÓPRIA role, só com as ações de DynamoDB/SNS/SQS que o
# código dela de fato usa — nada de uma role única compartilhada com a união de
# tudo. SnapStart habilitado (T008) onde faz sentido (API Gateway/EventBridge;
# não no consumidor SQS, ver nota abaixo).
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
  dynamodb_table_arn = aws_dynamodb_table.vessel_management.arn
  dynamodb_gsi1_arn  = "${aws_dynamodb_table.vessel_management.arn}/index/GSI1"
}

# ---------------------------------------------------------------------------
# api — VesselController/AvailabilityController/etc. (T047-T051). Único Lambda
# que publica eventos de domínio via SnsEventListener (T052-T055), por isso é
# o único com sns:Publish. Não faz Scan (findAll é só do advisory_job).
# ---------------------------------------------------------------------------

resource "aws_iam_role" "api_lambda" {
  name               = "${var.project_name}-vessel-mgmt-api-lambda-${var.environment}"
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
        "dynamodb:TransactGetItems",
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
        aws_sns_topic.vessel_availability_changed.arn,
        aws_sns_topic.vessel_seatlimit_changed.arn,
        aws_sns_topic.vessel_cancellation_operator_initiated.arn,
        aws_sns_topic.vessel_transfer_viable.arn,
        aws_sns_topic.vessel_recebedor_changed.arn,
      ]
    }]
  })
}

# Função que atende a API HTTP (VesselController, AvailabilityController, etc. — T047-T051).
# LIMITAÇÃO CONHECIDA (ver pom.xml): os controllers rodam via Spring MVC/DispatcherServlet,
# validados localmente via Tomcat embarcado + MockMvc — a ponte FunctionInvoker (só o
# adaptador puro, sem spring-cloud-function-web) até o DispatcherServlet dentro do Lambda
# real não foi validada nesta fase. Revisar com aws-serverless-java-container-springboot3
# antes do primeiro deploy.
resource "aws_lambda_function" "api" {
  function_name = "${var.project_name}-vessel-management-api-${var.environment}"
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
      SPRING_CLOUD_FUNCTION_DEFINITION               = "apiHandler"
      DYNAMODB_TABLE_NAME                            = aws_dynamodb_table.vessel_management.name
      SNS_VESSEL_AVAILABILITY_CHANGED_ARN            = aws_sns_topic.vessel_availability_changed.arn
      SNS_VESSEL_SEATLIMIT_CHANGED_ARN               = aws_sns_topic.vessel_seatlimit_changed.arn
      SNS_VESSEL_CANCELLATION_OPERATOR_INITIATED_ARN = aws_sns_topic.vessel_cancellation_operator_initiated.arn
      SNS_VESSEL_TRANSFER_VIABLE_ARN                 = aws_sns_topic.vessel_transfer_viable.arn
      SNS_VESSEL_RECEBEDOR_CHANGED_ARN               = aws_sns_topic.vessel_recebedor_changed.arn
    }
  }

  publish = true

  snap_start {
    apply_on = "PublishedVersions"
  }
}

# ---------------------------------------------------------------------------
# advisory_job — AdvisoryCalculationJob (T057), acionado pelo EventBridge
# Scheduler (infra/eventbridge.tf). Único que precisa de Scan (VesselRepository
# .findAll) e do único que escreve ADVISORY# via AdvisoryRepository.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "advisory_job_lambda" {
  name               = "${var.project_name}-vessel-mgmt-advisory-job-lambda-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "advisory_job_lambda_basic_execution" {
  role       = aws_iam_role.advisory_job_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "advisory_job_lambda_dynamodb" {
  name = "dynamodb"
  role = aws_iam_role.advisory_job_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:Scan",
        "dynamodb:GetItem",
        "dynamodb:PutItem",
      ]
      Resource = [local.dynamodb_table_arn]
    }]
  })
}

# Função acionada pelo EventBridge Scheduler para recalcular o WeatherTideAdvisory
# (FR-006) via Stormglass — job assíncrono, nunca escreve em DeclaredAvailability
# (Princípio I). Implementada em T056/T057.
resource "aws_lambda_function" "advisory_job" {
  function_name = "${var.project_name}-vessel-management-advisory-job-${var.environment}"
  role          = aws_iam_role.advisory_job_lambda.arn
  handler       = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
  runtime       = "java21"
  architectures = ["x86_64"]

  filename         = var.lambda_artifact_path
  source_code_hash = filebase64sha256(var.lambda_artifact_path)

  memory_size = 512
  timeout     = 60

  environment {
    variables = {
      SPRING_CLOUD_FUNCTION_DEFINITION = "advisoryCalculationJob"
      DYNAMODB_TABLE_NAME              = aws_dynamodb_table.vessel_management.name
      STORMGLASS_API_KEY               = var.stormglass_api_key
    }
  }

  publish = true

  snap_start {
    apply_on = "PublishedVersions"
  }
}

# ---------------------------------------------------------------------------
# booking_events_consumer — BookingEventsConsumer (T059b), acionado pela fila
# SQS booking_events (infra/sqs.tf, T006b). Só precisa incrementar/decrementar
# o contador via UpdateItem — nunca Query/Scan/TransactWriteItems.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "booking_events_consumer_lambda" {
  name               = "${var.project_name}-vessel-mgmt-booking-events-lambda-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "booking_events_consumer_basic_execution" {
  role       = aws_iam_role.booking_events_consumer_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "booking_events_consumer_dynamodb" {
  name = "dynamodb"
  role = aws_iam_role.booking_events_consumer_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["dynamodb:UpdateItem", "dynamodb:GetItem"]
      Resource = [local.dynamodb_table_arn]
    }]
  })
}

resource "aws_iam_role_policy" "booking_events_consumer_sqs" {
  name = "sqs"
  role = aws_iam_role.booking_events_consumer_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
      ]
      Resource = aws_sqs_queue.booking_events.arn
    }]
  })
}

# Função acionada pela fila SQS booking_events (infra/sqs.tf, T006b) — T059b consome
# `booking.confirmed` hoje; T059 (`booking.transferred`/`booking.cancelled`) usa a
# mesma function assim que o payload combinado com tasks-booking.md T055 existir.
# SnapStart não é usado aqui: event source mapping do SQS não invoca via alias/versão
# publicada da mesma forma que API Gateway, então o ganho de SnapStart é marginal para
# esse padrão de acionamento (poucas invocações, não sensíveis a cold start de borda).
resource "aws_lambda_function" "booking_events_consumer" {
  function_name = "${var.project_name}-vessel-management-booking-events-${var.environment}"
  role          = aws_iam_role.booking_events_consumer_lambda.arn
  handler       = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
  runtime       = "java21"
  architectures = ["x86_64"]

  filename         = var.lambda_artifact_path
  source_code_hash = filebase64sha256(var.lambda_artifact_path)

  memory_size = 512
  timeout     = 60

  environment {
    variables = {
      SPRING_CLOUD_FUNCTION_DEFINITION = "bookingEventsConsumer"
      DYNAMODB_TABLE_NAME              = aws_dynamodb_table.vessel_management.name
    }
  }
}

resource "aws_lambda_event_source_mapping" "booking_events_to_consumer" {
  event_source_arn = aws_sqs_queue.booking_events.arn
  function_name    = aws_lambda_function.booking_events_consumer.arn
  batch_size       = 10
}
