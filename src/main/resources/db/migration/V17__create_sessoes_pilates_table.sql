CREATE TABLE sessoes_pilates (
    id                    BIGSERIAL    PRIMARY KEY,
    paciente_id           BIGINT       NOT NULL REFERENCES pacientes(id),
    profissional_id       BIGINT       REFERENCES profissionais(id),
    plano_tratamento_id   BIGINT       REFERENCES planos_tratamento(id),
    tipo                  VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'AGENDADA',
    data                  DATE         NOT NULL,
    horario               TIME,
    local                 VARCHAR(100),
    duracao_minutos       INTEGER,
    observacoes           TEXT,
    evolucao              TEXT,
    data_criacao          TIMESTAMP    NOT NULL,
    data_atualizacao      TIMESTAMP
);

CREATE INDEX idx_sessoes_pilates_paciente_id
    ON sessoes_pilates(paciente_id);

CREATE INDEX idx_sessoes_pilates_data
    ON sessoes_pilates(data);
