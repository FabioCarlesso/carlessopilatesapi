CREATE TABLE pagamentos (
    id              BIGSERIAL       PRIMARY KEY,
    paciente_id     BIGINT          NOT NULL REFERENCES pacientes(id),
    plano_id        BIGINT          NOT NULL REFERENCES planos(id),
    valor           DECIMAL(10,2)   NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDENTE',
    data_pagamento  DATE,
    data_vencimento DATE            NOT NULL,
    periodo_inicio  DATE            NOT NULL,
    periodo_fim     DATE            NOT NULL,
    UNIQUE (plano_id, periodo_inicio)
);
