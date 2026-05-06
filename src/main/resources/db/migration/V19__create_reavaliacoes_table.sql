CREATE TABLE reavaliacoes (
    id                               BIGSERIAL    PRIMARY KEY,
    paciente_id                      BIGINT       NOT NULL REFERENCES pacientes(id),
    avaliacao_fisioterapeutica_id    BIGINT       REFERENCES avaliacoes_fisioterapeuticas(id),
    plano_tratamento_id              BIGINT       REFERENCES planos_tratamento(id),
    data_reavaliacao                 DATE         NOT NULL,
    comparativo_avaliacao_anterior   TEXT,
    evolucao_dor                     TEXT,
    evolucao_forca                   TEXT,
    evolucao_mobilidade              TEXT,
    evolucao_funcional               TEXT,
    objetivos_alcancados             TEXT,
    pontos_atencao                   TEXT,
    ajustes_recomendados             TEXT,
    observacoes_gerais               TEXT,
    data_criacao                     TIMESTAMP    NOT NULL,
    data_atualizacao                 TIMESTAMP
);

CREATE INDEX idx_reavaliacoes_paciente_id
    ON reavaliacoes(paciente_id);

CREATE INDEX idx_reavaliacoes_avaliacao_fisioterapeutica_id
    ON reavaliacoes(avaliacao_fisioterapeutica_id);

CREATE INDEX idx_reavaliacoes_plano_tratamento_id
    ON reavaliacoes(plano_tratamento_id);
