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

# Função que atende a API HTTP (VesselController, AvailabilityController, etc. — Fase 3.4).
# Placeholder de handler ("apiHandler", ver VesselManagementApplication.java) até os
# controllers reais existirem.
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
# (Princípio I). Lógica real entra em T057; aqui só a função vazia (placeholder
# "advisoryCalculationJob").
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
