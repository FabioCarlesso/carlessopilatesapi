-- Disponibilidade semanal dos profissionais (#216): as faixas de dia da semana e horário em que cada
-- profissional atende. É configuração, não histórico — a exclusão é física.
-- dia_semana guarda o nome do java.time.DayOfWeek (MONDAY..SUNDAY), como lista_espera.dia_semana.
-- A não sobreposição de faixas do mesmo profissional no mesmo dia é garantida pela aplicação, com
-- SELECT ... FOR UPDATE no profissional (mesmo padrão da capacidade, #106), e não por exclusion
-- constraint: o PostgreSQL não tem tipo de intervalo para TIME, e a constraint exigiria a extensão
-- btree_gist e um range construído sobre a hora — complexidade que a checagem serializada dispensa.
CREATE TABLE disponibilidades_profissional (
    id               BIGSERIAL    PRIMARY KEY,
    profissional_id  BIGINT       NOT NULL REFERENCES profissionais(id),
    dia_semana       VARCHAR(20)  NOT NULL,
    hora_inicio      TIME         NOT NULL,
    hora_fim         TIME         NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    data_criacao     TIMESTAMP,
    data_atualizacao TIMESTAMP,
    criado_por       VARCHAR(255),
    alterado_por     VARCHAR(255),
    CONSTRAINT ck_disponibilidades_profissional_dia_semana CHECK (
        dia_semana IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT ck_disponibilidades_profissional_faixa CHECK (hora_inicio < hora_fim)
);

-- Toda consulta da tabela filtra por profissional, e a de sobreposição também por dia.
CREATE INDEX idx_disponibilidades_profissional_dia
    ON disponibilidades_profissional (profissional_id, dia_semana, hora_inicio);
