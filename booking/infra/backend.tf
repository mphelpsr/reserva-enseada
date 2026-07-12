# Backend remoto de state, compartilhado entre módulos via bucket único e
# diferenciado por `key` (cada módulo tem seu próprio arquivo de state e seu
# próprio workspace lógico). Reaproveita o bootstrap já aplicado pelo
# vessel-management (bucket S3 + tabela DynamoDB de lock) — ver
# vessel-management/infra/bootstrap. Não é necessário um bootstrap próprio do
# booking: bucket e tabela de lock são compartilhados entre todos os módulos,
# só a `key` do state muda.

terraform {
  backend "s3" {
    bucket         = "reserva-enseada-terraform-state"
    key            = "booking/terraform.tfstate"
    region         = "sa-east-1"
    dynamodb_table = "reserva-enseada-terraform-locks"
    encrypt        = true
  }
}
