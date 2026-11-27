-- Fisioterapeuta padrão do paciente (#245).
-- Na prática cada paciente é atendido sempre pelo mesmo profissional; a troca é exceção (folga, ausência). As aulas
-- geradas pelo pagamento passam a nascer com esse profissional, respeitando a capacidade por horário e a
-- disponibilidade. Nulo para todos os existentes: o padrão é escolhido pela recepção, sem preenchimento automático.
ALTER TABLE pacientes ADD COLUMN profissional_padrao_id BIGINT REFERENCES profissionais(id);
CREATE INDEX idx_pacientes_profissional_padrao ON pacientes (profissional_padrao_id);
