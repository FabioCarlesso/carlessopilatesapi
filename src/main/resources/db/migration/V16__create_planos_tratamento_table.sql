CREATE TABLE planos_tratamento (
    id                        BIGSERIAL    PRIMARY KEY,
    paciente_id               BIGINT       NOT NULL REFERENCES pacientes(id),
    data_inicio               DATE         NOT NULL,
    data_fim_prevista         DATE,
    objetivos_tratamento      TEXT         NOT NULL,
    intervencoes_planejadas   TEXT,
    numero_sessoes_previstas  INTEGER,
    frequencia_sessoes        VARCHAR(100),
    responsavel_tratamento    VARCHAR(255),
    observacoes               TEXT,
    data_criacao              TIMESTAMP    NOT NULL,
    data_atualizacao          TIMESTAMP,
    ativo                     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_planos_tratamento_paciente_id
    ON planos_tratamento(paciente_id);
