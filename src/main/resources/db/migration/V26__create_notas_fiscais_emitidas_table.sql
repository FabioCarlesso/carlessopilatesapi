CREATE TABLE notas_fiscais_emitidas (
    id               BIGSERIAL     PRIMARY KEY,
    paciente_id      BIGINT        NOT NULL REFERENCES pacientes(id),
    competencia      DATE          NOT NULL,
    numero_nota      VARCHAR(60),
    data_emissao     DATE          NOT NULL,
    valor            DECIMAL(10,2),
    observacoes      TEXT,
    data_criacao     TIMESTAMP     NOT NULL,
    data_atualizacao TIMESTAMP,
    UNIQUE (paciente_id, competencia)
);

CREATE INDEX idx_notas_fiscais_emitidas_paciente ON notas_fiscais_emitidas (paciente_id);
