output "dynamodb_table_name" {
  value = aws_dynamodb_table.vessel_management.name
}

output "api_endpoint" {
  value = aws_apigatewayv2_api.vessel_management.api_endpoint
}

output "owner_user_pool_id" {
  value = aws_cognito_user_pool.owner.id
}

output "owner_user_pool_client_id" {
  value = aws_cognito_user_pool_client.owner_panel.id
}

output "sns_topic_arns" {
  description = "ARNs dos tópicos publicados por este módulo, para o módulo booking assinar (specs/002-booking/tasks.md T005)"
  value = {
    vessel_availability_changed            = aws_sns_topic.vessel_availability_changed.arn
    vessel_seatlimit_changed               = aws_sns_topic.vessel_seatlimit_changed.arn
    vessel_cancellation_operator_initiated = aws_sns_topic.vessel_cancellation_operator_initiated.arn
    vessel_transfer_viable                 = aws_sns_topic.vessel_transfer_viable.arn
  }
}

output "booking_events_queue_arn" {
  value = aws_sqs_queue.booking_events.arn
}
