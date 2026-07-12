# Cognito User Pool do comprador — isolado do pool do proprietário
# (vessel-management), decisão confirmada em plan-booking.md (Phase 0):
# personas e fluxos de auth completamente diferentes (app mobile do
# comprador vs. painel desktop do proprietário); um CPF que um dia precise
# ser proprietário E comprador vira um vínculo de dados entre contas, não um
# usuário único compartilhando pool.

resource "aws_cognito_user_pool" "buyer" {
  name = "${var.project_name}-booking-buyer-${var.environment}"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 10
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = false
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }
}

resource "aws_cognito_user_pool_client" "buyer_app" {
  name         = "${var.project_name}-booking-buyer-app-${var.environment}"
  user_pool_id = aws_cognito_user_pool.buyer.id

  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]

  generate_secret                      = false
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["COGNITO"]
  callback_urls                        = var.buyer_app_callback_urls
}

resource "aws_cognito_user_pool_domain" "buyer" {
  domain       = "${var.project_name}-booking-buyer-${var.environment}"
  user_pool_id = aws_cognito_user_pool.buyer.id
}
