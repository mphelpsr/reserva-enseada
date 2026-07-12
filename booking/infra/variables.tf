variable "aws_region" {
  description = "Região AWS onde a infraestrutura do módulo booking é provisionada"
  type        = string
  default     = "sa-east-1"
}

variable "environment" {
  description = "Nome do ambiente (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "project_name" {
  description = "Prefixo usado no nome dos recursos"
  type        = string
  default     = "reserva-enseada"
}

variable "buyer_app_callback_urls" {
  description = "URLs de callback OAuth do app do comprador (mobile React, Princípio III) — definir por ambiente"
  type        = list(string)
  default     = ["http://localhost:3000/callback"]
}

variable "lambda_artifact_path" {
  description = "Caminho local do jar empacotado para AWS Lambda, gerado por `mvn package` (ver pom.xml)"
  type        = string
  default     = "../target/booking-aws.jar"
}

variable "pagarme_api_key" {
  description = "API key do Pagar.me (T056) — split de pagamento FR-015. Vazio impede deploy útil da Lambda de confirmação, mas não bloqueia terraform apply (fica pra configurar antes do primeiro uso real)."
  type        = string
  default     = ""
  sensitive   = true
}

variable "platform_commission_percentage" {
  description = "Comissão da plataforma sobre cada venda confirmada (FR-015) — configurável, faixa de mercado 10-15%, nunca hardcoded no código"
  type        = number
  default     = 12
}

variable "ses_domain" {
  description = "Domínio verificado no SES para envio de e-mails transacionais (FR-010, T008/T057) — vazio pula a verificação de domínio (dev local sem SES real)"
  type        = string
  default     = ""
}
