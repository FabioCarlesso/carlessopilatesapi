-- Horário das aulas (issue #106). O plano guarda um horário único, aplicado a todos os seus dias da semana, e a
-- geração de aulas copia esse valor para cada aula; remarcar pode trocá-lo aula a aula.
-- Ambas as colunas são nulas de propósito: aulas e planos existentes não têm como recuperar o horário real, e um
-- valor padrão inventaria uma agenda e, com ela, conflitos de capacidade que nunca existiram. Aula sem horário
-- continua consultável e fica fora da regra de capacidade.
-- A UNIQUE (paciente_id, data) da V5 é mantida: o paciente segue com no máximo uma aula por dia.
ALTER TABLE planos ADD COLUMN horario TIME;
ALTER TABLE aulas ADD COLUMN horario TIME;
