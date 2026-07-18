-- Foto da análise postural (MVP: bytea no próprio banco, conforme decisão em
-- docs/simetrografo-virtual.md). O upload/download tem issue própria; aqui as
-- colunas existem para que a API saiba se a foto já foi enviada (temFoto) e
-- possa exigi-la na conclusão da análise.
ALTER TABLE avaliacoes_posturais
    ADD COLUMN foto              BYTEA,
    ADD COLUMN foto_content_type VARCHAR(100),
    -- Razão largura/altura da foto: os landmarks são normalizados (0 a 1), então
    -- sem essa proporção os ângulos ficariam distorcidos em fotos não quadradas.
    ADD COLUMN proporcao_imagem  NUMERIC(6,4) CHECK (proporcao_imagem > 0);
