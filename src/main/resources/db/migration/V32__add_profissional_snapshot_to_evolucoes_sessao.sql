-- Snapshot do profissional que registrou a evolução. Evolução de sessão é documento
-- clínico: derivar nome/registro do cadastro atual faria uma edição no cadastro
-- reescrever retroativamente todo o histórico. Por isso os dados são congelados aqui.
-- A FK em profissional_id permanece para garantir integridade referencial —
-- profissionais são inativados (ativo = false), nunca removidos.
ALTER TABLE evolucoes_sessao
    ADD COLUMN profissional_id              BIGINT REFERENCES profissionais(id),
    ADD COLUMN profissional_nome            VARCHAR(255),
    ADD COLUMN profissional_numero_registro VARCHAR(30);

-- Backfill best-effort a partir da sessão. `profissional_numero_registro` fica NULL:
-- o dado não existia à época do registro e não deve ser inventado retroativamente.
UPDATE evolucoes_sessao e
   SET profissional_id   = s.profissional_id,
       profissional_nome = p.nome
  FROM sessoes_pilates s
  JOIN profissionais p ON p.id = s.profissional_id
 WHERE e.sessao_id = s.id;

CREATE INDEX IF NOT EXISTS idx_evolucoes_sessao_profissional_id ON evolucoes_sessao(profissional_id);
