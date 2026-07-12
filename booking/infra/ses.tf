# Verificação de domínio SES + templates de e-mail transacional (FR-010:
# notificar o comprador em cada mudança relevante de status — confirmada,
# cancelada, transferida). Decisão de Phase 0 (plan.md): e-mail via SES no
# MVP, mais simples de operar (Princípio VI) sem dependência de app mobile
# nativo/push para o primeiro lançamento.
#
# A verificação de domínio é condicionada a `var.ses_domain` — vazio (default)
# pula o provisionamento, permitindo `terraform apply` em dev/local sem um
# domínio real configurado. O conteúdo final de cada template (dados reais do
# proprietário/motivo/embarcação) é preenchido pela aplicação em T057 — aqui
# só a casca (assunto + corpo com placeholders) via `aws_ses_template`.

resource "aws_ses_domain_identity" "transactional" {
  count  = var.ses_domain != "" ? 1 : 0
  domain = var.ses_domain
}

resource "aws_ses_template" "booking_confirmed" {
  name    = "${var.project_name}-booking-confirmed-${var.environment}"
  subject = "Reserva confirmada — {{vesselName}}"
  html    = "<p>Olá {{buyerName}},</p><p>Sua reserva para {{vesselName}} no dia {{data}} ({{tipoPasseio}}) foi confirmada.</p>"
  text    = "Olá {{buyerName}}, sua reserva para {{vesselName}} no dia {{data}} ({{tipoPasseio}}) foi confirmada."
}

resource "aws_ses_template" "booking_cancelled" {
  name    = "${var.project_name}-booking-cancelled-${var.environment}"
  subject = "Reserva cancelada — {{vesselName}}"
  html    = "<p>Olá {{buyerName}},</p><p>Sua reserva para {{vesselName}} no dia {{data}} foi cancelada. Motivo: {{motivo}}. O reembolso integral já foi processado.</p>"
  text    = "Olá {{buyerName}}, sua reserva para {{vesselName}} no dia {{data}} foi cancelada. Motivo: {{motivo}}. O reembolso integral já foi processado."
}

resource "aws_ses_template" "booking_transferred" {
  name    = "${var.project_name}-booking-transferred-${var.environment}"
  subject = "Sua reserva foi transferida — {{targetVesselName}}"
  html    = "<p>Olá {{buyerName}},</p><p>Sua reserva do dia {{data}} foi transferida para {{targetVesselName}}, mantendo o mesmo horário e condições.</p>"
  text    = "Olá {{buyerName}}, sua reserva do dia {{data}} foi transferida para {{targetVesselName}}, mantendo o mesmo horário e condições."
}
