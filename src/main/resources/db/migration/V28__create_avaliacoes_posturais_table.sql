CREATE TABLE avaliacoes_posturais (
    id                             BIGSERIAL      PRIMARY KEY,
    avaliacao_fisioterapeutica_id  BIGINT         NOT NULL REFERENCES avaliacoes_fisioterapeuticas(id),
    vista                          VARCHAR(20)    NOT NULL,
    status                         VARCHAR(20)    NOT NULL DEFAULT 'RASCUNHO',
    landmarks                      JSONB,
    linha_prumo_x                  NUMERIC(6,5)   CHECK (linha_prumo_x BETWEEN 0 AND 1),
    calibracao_cm_por_unidade      NUMERIC(10,4)  CHECK (calibracao_cm_por_unidade > 0),
    observacoes                    TEXT,
    ativo                          BOOLEAN        NOT NULL DEFAULT TRUE,
    data_criacao                   TIMESTAMP      NOT NULL,
    data_atualizacao               TIMESTAMP
);

CREATE INDEX idx_avaliacoes_posturais_avaliacao_fisioterapeutica_id
    ON avaliacoes_posturais(avaliacao_fisioterapeutica_id);

-- No máximo uma análise ativa por vista dentro da mesma avaliação; análises
-- canceladas (ativo = false) ficam fora do índice e permitem recriação.
CREATE UNIQUE INDEX avaliacoes_posturais_avaliacao_vista_uidx
    ON avaliacoes_posturais(avaliacao_fisioterapeutica_id, vista)
    WHERE ativo = TRUE;
