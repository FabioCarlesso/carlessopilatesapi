CREATE TABLE pacientes (
    id              BIGSERIAL       PRIMARY KEY,
    nome            VARCHAR(255)    NOT NULL,
    email           VARCHAR(255)    NOT NULL UNIQUE,
    cpf             VARCHAR(14)     NOT NULL UNIQUE,
    telefone        VARCHAR(20),
    data_nascimento DATE,
    logradouro      VARCHAR(255),
    numero          VARCHAR(20),
    bairro          VARCHAR(100),
    cidade          VARCHAR(100),
    uf              CHAR(2),
    cep             VARCHAR(9),
    ativo           BOOLEAN         NOT NULL DEFAULT TRUE
);
