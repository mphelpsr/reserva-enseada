# Cognito User Pool do proprietário — isolado do pool do comprador (booking),
# decisão confirmada em plan-booking.md.

resource "aws_cognito_user_pool" "owner" {
  name = "${var.project_name}-vessel-owner-${var.environment}"

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

resource "aws_cognito_user_pool_client" "owner_panel" {
  name         = "${var.project_name}-vessel-owner-panel-${var.environment}"
  user_pool_id = aws_cognito_user_pool.owner.id

  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]

  generate_secret                      = false
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["COGNITO"]
  callback_urls                        = var.owner_panel_callback_urls
}

resource "aws_cognito_user_pool_domain" "owner" {
  domain       = "${var.project_name}-vessel-owner-${var.environment}"
  user_pool_id = aws_cognito_user_pool.owner.id
}
