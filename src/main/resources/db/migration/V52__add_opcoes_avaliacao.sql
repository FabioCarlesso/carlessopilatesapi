-- Opções pré-definidas na anamnese, na avaliação fisioterapêutica e na reavaliação (#248).
--
-- Cada campo estruturado guarda o name() de um enum de opção (RegiaoDor, CondicaoSaude, AchadoPostural...) e tem um
-- complemento de texto livre. Os campos de texto que já existiam não mudam: continuam valendo para os registros antigos
-- e para a importação do seufisio, e o texto antigo não é migrado para as opções.
--
-- Seleção múltipla vai em TEXT[] (consultável com = ANY(...) e indexável com GIN se um dia for preciso); os testes
-- funcionais, que são pares teste/resultado, em JSONB. As listas nascem NOT NULL DEFAULT vazio, então os registros já
-- gravados ficam com lista vazia, e não nula. Não há CHECK com os valores: a lista de opções evolui no código (uma
-- opção aposentada continua gravada nos registros que a usam), e a validação contra o enum é feita na escrita.
--
-- Seleção única leva o sufixo _opcao onde a tabela já tem uma coluna de texto com o mesmo nome (nivel_atividade_fisica,
-- equilibrio, coordenacao_motora, padrao_respiratorio), e objetivos múltiplos viram objetivos_opcoes pelo mesmo motivo.
-- A reavaliação usa os mesmos nomes da avaliação para que as duas sejam comparáveis campo a campo.

ALTER TABLE anamneses
    ADD COLUMN regioes_dor                        TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN regioes_dor_complemento            TEXT,
    ADD COLUMN condicoes_saude                    TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN condicoes_saude_complemento        TEXT,
    ADD COLUMN nivel_atividade_fisica_opcao       VARCHAR(40),
    ADD COLUMN nivel_atividade_fisica_complemento TEXT,
    ADD COLUMN objetivos_opcoes                   TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN objetivos_complemento              TEXT;

ALTER TABLE avaliacoes_fisioterapeuticas
    ADD COLUMN achados_posturais               TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN achados_posturais_complemento   TEXT,
    ADD COLUMN padrao_respiratorio_opcao       VARCHAR(40),
    ADD COLUMN padrao_respiratorio_complemento TEXT,
    ADD COLUMN equilibrio_opcao                VARCHAR(40),
    ADD COLUMN equilibrio_complemento          TEXT,
    ADD COLUMN coordenacao_motora_opcao        VARCHAR(40),
    ADD COLUMN coordenacao_motora_complemento  TEXT,
    ADD COLUMN testes_funcionais               JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN testes_funcionais_complemento   TEXT;

ALTER TABLE reavaliacoes
    ADD COLUMN regioes_dor                     TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN regioes_dor_complemento         TEXT,
    ADD COLUMN achados_posturais               TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN achados_posturais_complemento   TEXT,
    ADD COLUMN padrao_respiratorio_opcao       VARCHAR(40),
    ADD COLUMN padrao_respiratorio_complemento TEXT,
    ADD COLUMN equilibrio_opcao                VARCHAR(40),
    ADD COLUMN equilibrio_complemento          TEXT,
    ADD COLUMN coordenacao_motora_opcao        VARCHAR(40),
    ADD COLUMN coordenacao_motora_complemento  TEXT,
    ADD COLUMN testes_funcionais               JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN testes_funcionais_complemento   TEXT;
