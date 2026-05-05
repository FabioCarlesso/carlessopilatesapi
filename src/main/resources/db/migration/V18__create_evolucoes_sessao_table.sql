CREATE TABLE evolucoes_sessao (
    id                          BIGSERIAL   PRIMARY KEY,
    sessao_id                   BIGINT      NOT NULL UNIQUE REFERENCES sessoes_pilates(id) ON DELETE CASCADE,
    data_hora_registro          TIMESTAMP   NOT NULL,
    exercicios_realizados       TEXT,
    equipamentos_utilizados     TEXT,
    cargas_molas                TEXT,
    dor_antes                   INTEGER     CHECK (dor_antes BETWEEN 0 AND 10),
    dor_depois                  INTEGER     CHECK (dor_depois BETWEEN 0 AND 10),
    resposta_paciente           TEXT,
    intercorrencias             TEXT,
    orientacoes                 TEXT,
    observacoes_fisioterapeuta  TEXT,
    data_criacao                TIMESTAMP   NOT NULL,
    data_atualizacao            TIMESTAMP
);
