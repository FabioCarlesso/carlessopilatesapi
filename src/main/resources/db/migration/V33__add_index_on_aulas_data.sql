-- Índice de apoio à agenda do estúdio (GET /aulas?inicio=&fim=).
-- A UNIQUE (paciente_id, data) da V5 não serve a esse filtro: `data` não é a
-- coluna à esquerda, então um range de datas sem `paciente_id` — que é
-- justamente a chamada padrão do calendário — cai em Seq Scan.
-- Espelha o idx_sessoes_pilates_data que a V17 já criou para as sessões.
CREATE INDEX IF NOT EXISTS idx_aulas_data ON aulas(data);
