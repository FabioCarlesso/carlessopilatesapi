-- Número de registro no conselho profissional (CREFITO, CREF etc.).
-- Nullable e sem backfill: os profissionais já cadastrados seguem válidos sem o dado.
ALTER TABLE profissionais ADD COLUMN numero_registro VARCHAR(30);
