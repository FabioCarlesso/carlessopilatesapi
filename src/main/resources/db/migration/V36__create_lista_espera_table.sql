-- Lista de espera por dia da semana e faixa de horário: quem quer entrar num horário lotado e
-- aguarda a vaga que um cancelamento abrir.
-- data_entrada é a ordem de chegada da fila — gravada pelo servidor, nunca pelo cliente — e por isso
-- é TIMESTAMP, não DATE: duas inscrições no mesmo dia precisam de desempate.
-- ativo = false é a remoção lógica (DELETE /lista-espera/{id}), que preserva o histórico de quem foi
-- atendido a partir da fila.
CREATE TABLE lista_espera (
    id           BIGSERIAL     PRIMARY KEY,
    paciente_id  BIGINT        NOT NULL REFERENCES pacientes(id),
    dia_semana   VARCHAR(20)   NOT NULL,
    hora_inicio  TIME          NOT NULL,
    hora_fim     TIME          NOT NULL,
    data_entrada TIMESTAMP     NOT NULL,
    observacao   VARCHAR(500),
    ativo        BOOLEAN       NOT NULL DEFAULT TRUE
);

-- A consulta da fila filtra por dia da semana e cruza a faixa horária (hora_fim > :inicio AND hora_inicio < :fim).
CREATE INDEX idx_lista_espera_dia_hora ON lista_espera (dia_semana, hora_inicio);

-- FK usada pelo filtro por paciente e pelo join que recorta pacientes inativos.
CREATE INDEX idx_lista_espera_paciente ON lista_espera (paciente_id);
