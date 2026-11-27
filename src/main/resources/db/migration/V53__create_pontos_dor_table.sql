-- Pontos de dor marcados sobre o mapa corporal (#249).
--
-- Cada ponto pertence a exatamente um registro clínico — a avaliação fisioterapêutica OU a reavaliação em que foi
-- marcado —, e o CHECK garante isso no banco, não só na aplicação. paciente_id é redundante com o registro, mas
-- deixa o histórico do paciente numa consulta só, sem UNION entre as duas origens.
--
-- x e y são normalizados (0 a 1) em relação ao viewBox do desenho, o que torna o ponto independente do tamanho em que
-- o FE o exibe. desenho_versao guarda a versão do desenho em que o ponto foi marcado: quando o desenho for trocado, os
-- pontos antigos continuam abrindo sobre o desenho original.
--
-- ON DELETE CASCADE nas duas FKs: o ponto não tem sentido fora do registro que o originou. Hoje a aplicação não
-- exclui avaliações nem reavaliações; a cascata vale para a exclusão que vier a existir e para a feita à mão.
CREATE TABLE pontos_dor (
    id                              BIGSERIAL       PRIMARY KEY,
    paciente_id                     BIGINT          NOT NULL REFERENCES pacientes(id),
    avaliacao_fisioterapeutica_id   BIGINT          REFERENCES avaliacoes_fisioterapeuticas(id) ON DELETE CASCADE,
    reavaliacao_id                  BIGINT          REFERENCES reavaliacoes(id) ON DELETE CASCADE,
    vista                           VARCHAR(20)     NOT NULL
        CHECK (vista IN ('FRENTE', 'COSTAS', 'LATERAL_DIREITA', 'LATERAL_ESQUERDA')),
    x                               NUMERIC(7,6)    NOT NULL CHECK (x BETWEEN 0 AND 1),
    y                               NUMERIC(7,6)    NOT NULL CHECK (y BETWEEN 0 AND 1),
    desenho_versao                  INTEGER         NOT NULL CHECK (desenho_versao >= 1),
    intensidade                     INTEGER         CHECK (intensidade BETWEEN 0 AND 10),
    observacao                      VARCHAR(200),
    data_criacao                    TIMESTAMP       NOT NULL,
    data_atualizacao                TIMESTAMP,
    criado_por                      VARCHAR(255),
    alterado_por                    VARCHAR(255),
    CONSTRAINT ck_pontos_dor_um_registro
        CHECK ((avaliacao_fisioterapeutica_id IS NULL) <> (reavaliacao_id IS NULL))
);

CREATE INDEX idx_pontos_dor_paciente_id ON pontos_dor(paciente_id);
CREATE INDEX idx_pontos_dor_avaliacao_fisioterapeutica_id ON pontos_dor(avaliacao_fisioterapeutica_id);
CREATE INDEX idx_pontos_dor_reavaliacao_id ON pontos_dor(reavaliacao_id);
