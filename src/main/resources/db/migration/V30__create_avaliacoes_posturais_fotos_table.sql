-- Foto da análise postural em tabela própria (decisão em docs/simetrografo-virtual.md):
-- com o bytea fora de avaliacoes_posturais, listagens e buscas da análise nunca
-- carregam o binário. A coluna foto criada na V29 nunca foi populada (o upload
-- chega junto desta migration) e é removida; foto_content_type permanece na
-- tabela principal como marcador barato de "foto presente" (temFoto).
CREATE TABLE avaliacoes_posturais_fotos (
    id                     BIGSERIAL      PRIMARY KEY,
    avaliacao_postural_id  BIGINT         NOT NULL UNIQUE REFERENCES avaliacoes_posturais(id),
    conteudo               BYTEA          NOT NULL,
    content_type           VARCHAR(100)   NOT NULL,
    tamanho_bytes          INTEGER        NOT NULL CHECK (tamanho_bytes > 0),
    largura_px             INTEGER        NOT NULL CHECK (largura_px > 0),
    altura_px              INTEGER        NOT NULL CHECK (altura_px > 0),
    data_criacao           TIMESTAMP      NOT NULL
);

ALTER TABLE avaliacoes_posturais DROP COLUMN foto;
