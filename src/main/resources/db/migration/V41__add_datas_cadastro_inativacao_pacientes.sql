-- Datas de entrada e saída do paciente, para as séries de novos cadastros e evasão do dashboard (issue #202).
-- Até aqui só existia o booleano `ativo`: dava para saber quantos estão inativos, não quando saíram.
-- Ficam nulas nas linhas existentes de propósito: não há como reconstruir a data real, e um backfill com a data
-- do deploy inventaria um pico de cadastros e de evasão nesse mês. Esses pacientes não entram em nenhuma série.
ALTER TABLE pacientes ADD COLUMN data_cadastro DATE;
ALTER TABLE pacientes ADD COLUMN data_inativacao DATE;
