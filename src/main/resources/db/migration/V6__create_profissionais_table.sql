CREATE TABLE profissionais (
    id                          BIGSERIAL       PRIMARY KEY,
    nome                        VARCHAR(255)    NOT NULL,
    email                       VARCHAR(255)    NOT NULL UNIQUE,
    cpf                         VARCHAR(14)     NOT NULL UNIQUE,
    telefone                    VARCHAR(20),
    tipo_contrato               VARCHAR(30)     NOT NULL,
    percentual_pagamento_aula   NUMERIC(3,2)    NOT NULL,
    data_inicio                 DATE            NOT NULL,
    ativo                       BOOLEAN         NOT NULL DEFAULT TRUE
);
