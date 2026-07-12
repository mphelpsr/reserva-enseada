# Backend remoto de state, compartilhado entre módulos via bucket único e
# diferenciado por `key` (cada módulo tem seu próprio arquivo de state e seu
# próprio workspace lógico). Pressupõe que `infra/bootstrap` já foi aplicado
# (bucket S3 + tabela DynamoDB de lock existentes) — ver README do bootstrap.

terraform {
  backend "s3" {
    bucket         = "reserva-enseada-terraform-state"
    key            = "vessel-management/terraform.tfstate"
    region         = "sa-east-1"
    dynamodb_table = "reserva-enseada-terraform-locks"
    encrypt        = true
  }
}
