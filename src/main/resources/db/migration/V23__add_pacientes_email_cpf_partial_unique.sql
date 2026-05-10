CREATE UNIQUE INDEX IF NOT EXISTS pacientes_email_uidx ON pacientes (email) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS pacientes_cpf_uidx ON pacientes (cpf) WHERE cpf IS NOT NULL;
