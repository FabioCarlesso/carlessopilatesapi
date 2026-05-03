CREATE TABLE anamneses (
    id                    BIGSERIAL       PRIMARY KEY,
    paciente_id           BIGINT          NOT NULL UNIQUE REFERENCES pacientes(id),
    queixa_principal      TEXT            NOT NULL,
    historico_doencas     TEXT,
    historico_cirurgias   TEXT,
    historico_lesoes      TEXT,
    medicamentos_uso      TEXT,
    alergias              TEXT,
    nivel_atividade_fisica VARCHAR(50),
    restricoes_medicas    TEXT,
    objetivos             TEXT            NOT NULL,
    observacoes           TEXT,
    data_criacao          TIMESTAMP       NOT NULL,
    data_atualizacao      TIMESTAMP
);
