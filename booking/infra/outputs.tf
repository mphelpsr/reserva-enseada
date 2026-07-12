output "dynamodb_table_name" {
  value = aws_dynamodb_table.booking.name
}

output "api_endpoint" {
  value = aws_apigatewayv2_api.booking.api_endpoint
}

output "buyer_user_pool_id" {
  value = aws_cognito_user_pool.buyer.id
}

output "buyer_user_pool_client_id" {
  value = aws_cognito_user_pool_client.buyer_app.id
}

output "sns_topic_arns" {
  description = "ARNs dos tópicos publicados por este módulo, para o módulo vessel-management assinar (specs/001-vessel-management/tasks.md T006b/T059)"
  value = {
    booking_confirmed   = aws_sns_topic.booking_confirmed.arn
    booking_cancelled   = aws_sns_topic.booking_cancelled.arn
    booking_transferred = aws_sns_topic.booking_transferred.arn
  }
}

output "operator_events_queue_arn" {
  value = aws_sqs_queue.operator_events.arn
}
