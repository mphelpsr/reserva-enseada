# API Gateway HTTP API + integração com a Lambda "api" + autorizador Cognito
# (JWT, pool do comprador — T003). A rota catch-all abaixo cobre a Fase 3.1;
# rotas específicas por endpoint são adicionadas junto dos controllers na
# Fase 3.4 (T047-T048) e a validação fim-a-fim do autorizador em todas as
# rotas é fechada em T058.

resource "aws_apigatewayv2_api" "booking" {
  name          = "${var.project_name}-booking-${var.environment}"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "api_lambda" {
  api_id                 = aws_apigatewayv2_api.booking.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.api.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_authorizer" "buyer_cognito" {
  api_id           = aws_apigatewayv2_api.booking.id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "${var.project_name}-booking-buyer-authorizer-${var.environment}"

  jwt_configuration {
    audience = [aws_cognito_user_pool_client.buyer_app.id]
    issuer   = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.buyer.id}"
  }
}

resource "aws_apigatewayv2_route" "default_catch_all" {
  api_id             = aws_apigatewayv2_api.booking.id
  route_key          = "$default"
  target             = "integrations/${aws_apigatewayv2_integration.api_lambda.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.buyer_cognito.id
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.booking.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_lambda_permission" "api_gateway_invoke" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.api.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.booking.execution_arn}/*/*"
}
