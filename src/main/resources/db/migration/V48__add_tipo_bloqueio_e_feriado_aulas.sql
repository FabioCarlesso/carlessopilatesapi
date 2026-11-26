-- Aula de feriado conta como realizada (#244).
--
-- bloqueios_agenda.tipo: FERIADO ou OUTRO. Só o bloqueio FERIADO de dia inteiro marca aulas; manutenção ou evento não
--   é aula realizada. Os bloqueios existentes viram OUTRO: o motivo é texto livre e não dá para saber, com segurança,
--   quais eram feriados. A recepção reclassifica os que quiser.
-- aulas.feriado: a aula caiu num feriado e foi marcada realizada por isso (gera repasse, consome o pacote, não é falta).
-- aulas.bloqueio_feriado_id: o bloqueio que marcou a aula, para desfazer só essas ao excluir ou editar o bloqueio, sem
--   tocar nas aulas realizadas de verdade. ON DELETE SET NULL é só a rede de segurança: o serviço desfaz a marcação
--   antes de apagar o bloqueio.
ALTER TABLE bloqueios_agenda ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'OUTRO';
ALTER TABLE bloqueios_agenda ADD CONSTRAINT ck_bloqueios_agenda_tipo CHECK (tipo IN ('FERIADO', 'OUTRO'));

ALTER TABLE aulas ADD COLUMN feriado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE aulas ADD COLUMN bloqueio_feriado_id BIGINT REFERENCES bloqueios_agenda(id) ON DELETE SET NULL;
CREATE INDEX idx_aulas_bloqueio_feriado ON aulas (bloqueio_feriado_id);
