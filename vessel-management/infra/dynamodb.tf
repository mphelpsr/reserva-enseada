# Tabela single-table `VesselManagement` — schema completo em
# specs/001-vessel-management/plan.md (Phase 1 — Data Model).
# GSI1 suporta o access pattern "listar embarcações de um proprietário" (FR-010).

resource "aws_dynamodb_table" "vessel_management" {
  name         = "${var.project_name}-vessel-management-${var.environment}"
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

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "VesselManagement"
  }
}
