variable "aws_region" {
  description = "Região AWS onde a infraestrutura do módulo vessel-management é provisionada"
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

variable "owner_panel_callback_urls" {
  description = "URLs de callback OAuth do painel do proprietário (front-end desktop, React) — definir por ambiente"
  type        = list(string)
  default     = ["http://localhost:3000/callback"]
}

variable "lambda_artifact_path" {
  description = "Caminho local do jar empacotado para AWS Lambda, gerado por `mvn package` (ver pom.xml)"
  type        = string
  default     = "../target/vessel-management-aws.jar"
}

variable "stormglass_api_key" {
  description = "API key da Stormglass (T056) — vazio faz o advisory job pular o cálculo sem erro (Princípio I: advisory nunca bloqueia)"
  type        = string
  default     = ""
  sensitive   = true
}
