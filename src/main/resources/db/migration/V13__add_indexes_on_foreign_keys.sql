-- FK indexes: PostgreSQL does not create indexes on FK columns automatically.
-- Indexes on plano_dias_semana(plano_id), pagamentos(plano_id) and aulas(paciente_id)
-- are intentionally omitted: those columns are already covered as the leftmost prefix
-- of existing composite PK/UNIQUE indexes (V3, V4, V5).

CREATE INDEX IF NOT EXISTS idx_planos_paciente_id ON planos(paciente_id);
CREATE INDEX IF NOT EXISTS idx_pagamentos_paciente_id ON pagamentos(paciente_id);
CREATE INDEX IF NOT EXISTS idx_aulas_pagamento_id ON aulas(pagamento_id);
CREATE INDEX IF NOT EXISTS idx_aulas_profissional_id ON aulas(profissional_id);

-- High-frequency filter columns used by the scheduler and reports.
CREATE INDEX IF NOT EXISTS idx_pagamentos_status ON pagamentos(status);
CREATE INDEX IF NOT EXISTS idx_pagamentos_data_vencimento ON pagamentos(data_vencimento);
CREATE INDEX IF NOT EXISTS idx_aulas_realizada ON aulas(realizada);
