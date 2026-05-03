CREATE TABLE avaliacoes_fisioterapeuticas (
    id                               BIGSERIAL       PRIMARY KEY,
    paciente_id                      BIGINT          NOT NULL REFERENCES pacientes(id),
    data_avaliacao                   DATE            NOT NULL,
    queixa_funcional                 TEXT            NOT NULL,
    avaliacao_postural               TEXT,
    mobilidade_articular             TEXT,
    forca_muscular                   TEXT,
    flexibilidade                    TEXT,
    equilibrio                       TEXT,
    coordenacao_motora               TEXT,
    padrao_respiratorio              TEXT,
    escala_dor                       INTEGER         NOT NULL CHECK (escala_dor BETWEEN 0 AND 10),
    testes_funcionais_realizados     TEXT,
    diagnostico_fisioterapeutico     TEXT            NOT NULL,
    observacoes_gerais               TEXT,
    data_criacao                     TIMESTAMP       NOT NULL,
    data_atualizacao                 TIMESTAMP
);

CREATE INDEX idx_avaliacoes_fisioterapeuticas_paciente_id
    ON avaliacoes_fisioterapeuticas(paciente_id);
