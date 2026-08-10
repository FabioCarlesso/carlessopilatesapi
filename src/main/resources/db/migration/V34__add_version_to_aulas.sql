-- Locking otimista em `aulas`.
-- A linha passou a ter mais de um escritor concorrente: PATCH /aulas/{id}/realizar,
-- /profissional e agora /remarcar. Sem `version`, duas recepcionistas agindo sobre a
-- mesma aula no mesmo instante fazem last-write-wins silencioso — o caso caro é
-- realizar e remarcar ao mesmo tempo, que grava a aula como realizada numa data em
-- que ela não foi ministrada e leva o erro para o repasse do profissional.
-- DEFAULT 0 preenche as linhas existentes; o `ddl-auto=validate` exige a coluna NOT NULL.
ALTER TABLE aulas ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
