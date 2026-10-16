-- Locking otimista nas entidades editáveis (issue #101), no mesmo molde da V34 em `aulas`.
-- Sem `version`, dois PUTs concorrentes no mesmo registro fazem last-write-wins silencioso:
-- duas recepcionistas editando o mesmo paciente e a alteração de uma some sem aviso.
-- DEFAULT 0 preenche as linhas existentes sem tocar nos dados; o `ddl-auto=validate` exige
-- a coluna NOT NULL.
ALTER TABLE pacientes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE profissionais ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE anamneses ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE avaliacoes_fisioterapeuticas ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE planos_tratamento ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sessoes_pilates ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE evolucoes_sessao ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE reavaliacoes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE preferencias_usuario ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
