# Tabela single-table `Booking` — schema completo em specs/002-booking/plan.md
# (Phase 1 — Data Model). Tabela própria do módulo, sem transação cross-tabela
# com vessel-management (Princípio VI) — os dados de vessel-management usados
# aqui (disponibilidade, limite de vagas) são réplicas locais mantidas via
# eventos SQS (ver sqs.tf), nunca lidos por chamada síncrona.
#
# GSI1 (GSI1PK=BUYER#<buyerId>, GSI1SK=BOOKING#<bookingId>) suporta "minhas
# reservas" (FR-011) sem scan.
#
# TTL: item HOLD#<holdId> usa o atributo `ttl` (epoch seconds) para limpeza
# física em até 48h — não é a fonte de verdade de expiração do hold de 10min
# (FR-004): a aplicação sempre ignora holds com `expiresAt` vencido na
# leitura, e o job sweeper (T046, eventbridge.tf) roda a cada 1 min como
# reforço de higiene. Mesma ressalva já registrada em CLAUDE.md.

resource "aws_dynamodb_table" "booking" {
  name         = "${var.project_name}-booking-${var.environment}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"

  attribute {
    name = "PK"
    type = "S"
  }

  attribute {
    name = "SK"
    type = "S"
  }

  attribute {
    name = "GSI1PK"
    type = "S"
  }

  attribute {
    name = "GSI1SK"
    type = "S"
  }

  global_secondary_index {
    name            = "GSI1"
    hash_key        = "GSI1PK"
    range_key       = "GSI1SK"
    projection_type = "ALL"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "Booking"
  }
}
