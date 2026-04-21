CREATE TABLE planos (
    id                  BIGSERIAL       PRIMARY KEY,
    paciente_id         BIGINT          NOT NULL REFERENCES pacientes(id),
    tipo                VARCHAR(20)     NOT NULL,
    valor               DECIMAL(10,2)   NOT NULL,
    frequencia_semanal  VARCHAR(20)     NOT NULL,
    data_inicio         DATE            NOT NULL,
    ativo               BOOLEAN         NOT NULL DEFAULT TRUE
);

CREATE TABLE plano_dias_semana (
    plano_id    BIGINT      NOT NULL REFERENCES planos(id),
    dia_semana  VARCHAR(15) NOT NULL,
    PRIMARY KEY (plano_id, dia_semana)
);
