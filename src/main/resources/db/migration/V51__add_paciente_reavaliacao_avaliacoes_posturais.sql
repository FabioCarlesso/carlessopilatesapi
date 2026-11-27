-- Análise postural sem avaliação fisioterapêutica (#247).
--
-- A clínica quer usar o simetrógrafo solto, numa checagem rápida da postura, sem abrir uma avaliação completa. A
-- análise passa a pertencer sempre a um paciente (paciente_id NOT NULL) e o vínculo com a avaliação vira opcional;
-- entra também o vínculo opcional com uma reavaliação. O CHECK garante no máximo um dos dois: a análise é solta, da
-- avaliação ou da reavaliação, nunca das duas.
ALTER TABLE avaliacoes_posturais ADD COLUMN paciente_id BIGINT REFERENCES pacientes(id);

-- Toda análise existente nasceu dentro de uma avaliação (a coluna era NOT NULL até aqui), então o paciente dela é o da
-- avaliação, sem ambiguidade.
UPDATE avaliacoes_posturais ap
   SET paciente_id = af.paciente_id
  FROM avaliacoes_fisioterapeuticas af
 WHERE af.id = ap.avaliacao_fisioterapeutica_id
   AND ap.paciente_id IS NULL;

ALTER TABLE avaliacoes_posturais ALTER COLUMN paciente_id SET NOT NULL;
ALTER TABLE avaliacoes_posturais ALTER COLUMN avaliacao_fisioterapeutica_id DROP NOT NULL;

ALTER TABLE avaliacoes_posturais ADD COLUMN reavaliacao_id BIGINT REFERENCES reavaliacoes(id);
ALTER TABLE avaliacoes_posturais ADD CONSTRAINT ck_avaliacoes_posturais_um_vinculo
    CHECK (avaliacao_fisioterapeutica_id IS NULL OR reavaliacao_id IS NULL);

-- A listagem do paciente passa a ser a consulta principal da análise.
CREATE INDEX idx_avaliacoes_posturais_paciente_id ON avaliacoes_posturais(paciente_id);
CREATE INDEX idx_avaliacoes_posturais_reavaliacao_id ON avaliacoes_posturais(reavaliacao_id);

-- A mesma regra da avaliação (V28) vale para a reavaliação: no máximo uma análise ativa por vista. A análise solta fica
-- fora dos dois índices (NULL não colide), e o paciente pode ter várias da mesma vista ao longo do tempo.
CREATE UNIQUE INDEX avaliacoes_posturais_reavaliacao_vista_uidx
    ON avaliacoes_posturais(reavaliacao_id, vista)
    WHERE ativo = TRUE;
