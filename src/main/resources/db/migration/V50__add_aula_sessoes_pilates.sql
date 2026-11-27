-- Vínculo entre a aula e a sessão do atendimento (#246).
--
-- A aula é o compromisso da agenda e a base do financeiro (repasse, frequência, ocupação); a sessão, com a sua
-- evolução, é o registro clínico. Os dois cadastros continuam separados, e a sessão passa a apontar para a aula que
-- ela registra. Uma aula tem no máximo uma sessão (UNIQUE); a sessão sem aula — avaliação, reposição fora da agenda —
-- continua permitida (coluna anulável).
--
-- ON DELETE SET NULL é só a rede de segurança: a aplicação não exclui aulas, e se uma for apagada à mão o registro
-- clínico sobrevive como sessão avulsa, em vez de travar a exclusão ou sumir junto.
ALTER TABLE sessoes_pilates ADD COLUMN aula_id BIGINT REFERENCES aulas(id) ON DELETE SET NULL;
ALTER TABLE sessoes_pilates ADD CONSTRAINT uk_sessoes_pilates_aula UNIQUE (aula_id);

-- Vínculo das sessões já existentes, só quando a correspondência é inequívoca: a aula do mesmo paciente na mesma data
-- (a UNIQUE (paciente_id, data) de aulas garante que é no máximo uma) e uma única sessão não cancelada do paciente
-- nesse dia. Com duas sessões no dia não há como saber qual é a da aula, e as duas ficam avulsas — o relatório de aulas
-- sem evolução segue casando essas aulas por paciente + data. A aula de feriado (#244) fica de fora: ela não teve
-- atendimento, e a sessão daquele dia, se existir, não é dela. A sessão cancelada também: ela não registra a aula.
-- Cada sessão recebe no máximo uma aula e cada aula no máximo uma sessão, então o UPDATE não viola a UNIQUE acima.
UPDATE sessoes_pilates s
   SET aula_id = a.id
  FROM aulas a
 WHERE a.paciente_id = s.paciente_id
   AND a.data = s.data
   AND a.feriado = FALSE
   AND s.status <> 'CANCELADA'
   AND s.aula_id IS NULL
   AND (SELECT COUNT(*)
          FROM sessoes_pilates outra
         WHERE outra.paciente_id = s.paciente_id
           AND outra.data = s.data
           AND outra.status <> 'CANCELADA') = 1;
