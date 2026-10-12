-- Histórico de contatos de cobrança feitos pelo estúdio sobre um pagamento em aberto (issue #197).
-- É um log de inclusão: não há edição nem exclusão, correções entram como um novo contato.
-- data_contato é quando o contato aconteceu, informada pelo cliente; registrado_por e registrado_em são gravados
-- pelo servidor a partir do usuário autenticado. registrado_por guarda o e-mail como snapshot, e não uma FK para
-- users, para que o histórico não mude se o usuário for renomeado ou desativado.
CREATE TABLE contatos_cobranca (
    id             BIGSERIAL     PRIMARY KEY,
    pagamento_id   BIGINT        NOT NULL REFERENCES pagamentos(id),
    data_contato   DATE          NOT NULL,
    observacao     VARCHAR(500)  NOT NULL,
    registrado_por VARCHAR(255)  NOT NULL,
    registrado_em  TIMESTAMP     NOT NULL
);

-- FK usada pela listagem do histórico de um pagamento.
CREATE INDEX idx_contatos_cobranca_pagamento ON contatos_cobranca (pagamento_id);
