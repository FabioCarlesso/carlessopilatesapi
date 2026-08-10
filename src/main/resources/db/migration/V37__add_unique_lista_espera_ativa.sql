-- A checagem de duplicidade do service (SELECT seguido de INSERT) não é atômica: com requisições
-- concorrentes da mesma faixa, todas passam pelo SELECT antes de qualquer INSERT e o 409 documentado
-- deixa de valer. Oito POSTs simultâneos criaram três entradas idênticas antes deste índice.
--
-- O índice é PARCIAL (WHERE ativo) porque a saída da fila é lógica: sem o predicado, remover uma entrada
-- e reinscrever o mesmo paciente na mesma faixa esbarraria na linha inativa. Mesmo recurso que a V23 usa
-- para e-mail e CPF de paciente.
CREATE UNIQUE INDEX lista_espera_ativa_uidx
    ON lista_espera (paciente_id, dia_semana, hora_inicio, hora_fim)
    WHERE ativo;
