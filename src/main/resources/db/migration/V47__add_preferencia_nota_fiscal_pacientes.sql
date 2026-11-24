-- Preferência do paciente para a NFSE e cadastro de tomadores (#229).
-- Até aqui o relatório de NFSE exigia CPF de todo paciente que pagou na competência e respondia 422 inteiro quando
-- faltava um; e não havia onde registrar quem quer CPF na nota, nem em nome de quem.
--
-- tomadores: quem recebe a nota quando não é o próprio paciente — o responsável por alunos menores, a empresa que
--   paga as aulas de vários funcionários. Um tomador atende vários pacientes. CPF/CNPJ guardado só com dígitos
--   (11 ou 14, validado no serviço) e único.
-- cpf_na_nota: o paciente pediu CPF/CNPJ na nota. Default FALSE, inclusive para os existentes — os importados do
--   seufisio não tinham essa informação, e o estúdio marca quem deseja.
-- tomador_id: tomador do paciente; nulo = a nota sai no CPF do próprio paciente.
CREATE TABLE tomadores (
    id               BIGSERIAL    PRIMARY KEY,
    nome             VARCHAR(255) NOT NULL,
    cpf_cnpj         VARCHAR(14)  NOT NULL,
    email            VARCHAR(255),
    telefone         VARCHAR(30),
    observacoes      VARCHAR(500),
    ativo            BOOLEAN      NOT NULL DEFAULT TRUE,
    version          BIGINT       NOT NULL DEFAULT 0,
    data_criacao     TIMESTAMP,
    data_atualizacao TIMESTAMP,
    criado_por       VARCHAR(255),
    alterado_por     VARCHAR(255),
    CONSTRAINT uk_tomadores_cpf_cnpj UNIQUE (cpf_cnpj),
    CONSTRAINT ck_tomadores_cpf_cnpj CHECK (cpf_cnpj ~ '^([0-9]{11}|[0-9]{14})$')
);

ALTER TABLE pacientes ADD COLUMN cpf_na_nota BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pacientes ADD COLUMN tomador_id BIGINT REFERENCES tomadores(id);
CREATE INDEX idx_pacientes_tomador ON pacientes (tomador_id);
