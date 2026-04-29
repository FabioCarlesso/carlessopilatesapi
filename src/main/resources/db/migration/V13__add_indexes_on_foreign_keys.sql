CREATE INDEX idx_planos_paciente_id ON planos(paciente_id);
CREATE INDEX idx_plano_dias_semana_plano_id ON plano_dias_semana(plano_id);
CREATE INDEX idx_pagamentos_paciente_id ON pagamentos(paciente_id);
CREATE INDEX idx_pagamentos_plano_id ON pagamentos(plano_id);
CREATE INDEX idx_aulas_paciente_id ON aulas(paciente_id);
CREATE INDEX idx_aulas_pagamento_id ON aulas(pagamento_id);
CREATE INDEX idx_aulas_profissional_id ON aulas(profissional_id);
