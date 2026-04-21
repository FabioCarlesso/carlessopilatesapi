CREATE TABLE aulas (
    id              BIGSERIAL   PRIMARY KEY,
    paciente_id     BIGINT      NOT NULL REFERENCES pacientes(id),
    pagamento_id    BIGINT      NOT NULL REFERENCES pagamentos(id),
    data            DATE        NOT NULL,
    realizada       BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (paciente_id, data)
);
