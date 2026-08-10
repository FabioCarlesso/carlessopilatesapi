-- Bloqueios de agenda do estúdio: feriados, manutenção, eventos, férias coletivas.
-- O bloqueio é informativo (não recusa geração de aula nem agendamento de sessão), então
-- não há constraint de sobreposição: dois bloqueios podem cobrir o mesmo dia, e registrar
-- "feriado" e "manutenção" na mesma data é caso de uso, não erro.
-- hora_inicio/hora_fim nulos = dia inteiro; ambas preenchidas = faixa de horário.
CREATE TABLE bloqueios_agenda (
    id               BIGSERIAL     PRIMARY KEY,
    data_inicio      DATE          NOT NULL,
    data_fim         DATE          NOT NULL,
    hora_inicio      TIME,
    hora_fim         TIME,
    motivo           VARCHAR(200)  NOT NULL,
    data_criacao     TIMESTAMP     NOT NULL,
    data_atualizacao TIMESTAMP
);

-- A única consulta da tabela é a interseção com um período (data_inicio <= :fim AND data_fim >= :inicio).
CREATE INDEX idx_bloqueios_agenda_periodo ON bloqueios_agenda (data_inicio, data_fim);
