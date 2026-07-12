# IAM + funções Lambda de compute do módulo. SnapStart habilitado (T008) para
# mitigar cold start em Java (plan.md - Technical Context).
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

resource "aws_iam_role" "lambda_exec" {
  name               = "${var.project_name}-vessel-management-lambda-${var.environment}"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Função que atende a API HTTP (VesselController, AvailabilityController, etc. — T047-T051).
# LIMITAÇÃO CONHECIDA (ver pom.xml): os controllers rodam via Spring MVC/DispatcherServlet,
# validados localmente via Tomcat embarcado + MockMvc — a ponte FunctionInvoker (só o
# adaptador puro, sem spring-cloud-function-web) até o DispatcherServlet dentro do Lambda
# real não foi validada nesta fase. Revisar com aws-serverless-java-container-springboot3
# antes do primeiro deploy.
resource "aws_lambda_function" "api" {
  function_name = "${var.project_name}-vessel-management-api-${var.environment}"
  role          = aws_iam_role.lambda_exec.arn
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
      DYNAMODB_TABLE_NAME              = aws_dynamodb_table.vessel_management.name
    }
  }

  publish = true

  snap_start {
    apply_on = "PublishedVersions"
  }
}

# Função acionada pelo EventBridge Scheduler para recalcular o WeatherTideAdvisory
# (FR-006) via Stormglass — job assíncrono, nunca escreve em DeclaredAvailability
# (Princípio I). Implementada em T056/T057.
resource "aws_lambda_function" "advisory_job" {
  function_name = "${var.project_name}-vessel-management-advisory-job-${var.environment}"
  role          = aws_iam_role.lambda_exec.arn
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
    }
  }

  publish = true

  snap_start {
    apply_on = "PublishedVersions"
  }
}

# Função acionada pela fila SQS booking_events (infra/sqs.tf, T006b) — T059b consome
# `booking.confirmed` hoje; T059 (`booking.transferred`/`booking.cancelled`) usa a
# mesma function assim que o payload combinado com tasks-booking.md T055 existir.
# SnapStart não é usado aqui: event source mapping do SQS não invoca via alias/versão
# publicada da mesma forma que API Gateway, então o ganho de SnapStart é marginal para
# esse padrão de acionamento (poucas invocações, não sensíveis a cold start de borda).
resource "aws_lambda_function" "booking_events_consumer" {
  function_name = "${var.project_name}-vessel-management-booking-events-${var.environment}"
  role          = aws_iam_role.lambda_exec.arn
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

resource "aws_iam_role_policy" "booking_events_consumer_sqs" {
  name = "booking-events-consumer-sqs"
  role = aws_iam_role.lambda_exec.id

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

resource "aws_lambda_event_source_mapping" "booking_events_to_consumer" {
  event_source_arn = aws_sqs_queue.booking_events.arn
  function_name    = aws_lambda_function.booking_events_consumer.arn
  batch_size       = 10
}
