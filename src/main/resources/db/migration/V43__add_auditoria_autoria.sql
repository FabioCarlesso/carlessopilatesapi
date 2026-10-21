-- Auditoria de criação/alteração (#109): autor e timestamps preenchidos pelo Spring Data Auditing.
-- O autor é o e-mail do usuário autenticado (ou 'system' nos schedulers), sem FK para users, como em
-- contatos_cobranca.registrado_por: o registro de quem alterou não pode sumir se o usuário for removido.

-- Tabelas que já tinham data_criacao/data_atualizacao: as colunas e seus valores são reaproveitados,
-- só a autoria é nova. Linhas existentes ficam com autor nulo — não há como saber quem as criou.
ALTER TABLE anamneses ADD COLUMN criado_por VARCHAR(255), ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE avaliacoes_fisioterapeuticas ADD COLUMN criado_por VARCHAR(255), ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE avaliacoes_posturais ADD COLUMN criado_por VARCHAR(255), ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE bloqueios_agenda ADD COLUMN criado_por VARCHAR(255), ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE evolucoes_sessao ADD COLUMN criado_por VARCHAR(255), ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE notas_fiscais_emitidas ADD COLUMN criado_por VARCHAR(255), ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE planos_tratamento ADD COLUMN criado_por VARCHAR(255), ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE reavaliacoes ADD COLUMN criado_por VARCHAR(255), ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE sessoes_pilates ADD COLUMN criado_por VARCHAR(255), ADD COLUMN alterado_por VARCHAR(255);

-- Tabelas sem nenhum timestamp: ganham as quatro colunas, anuláveis e sem backfill. Uma data inventada
-- seria pior que a ausência dela; as linhas novas sempre as recebem pela aplicação.
ALTER TABLE pacientes
    ADD COLUMN data_criacao TIMESTAMP,
    ADD COLUMN data_atualizacao TIMESTAMP,
    ADD COLUMN criado_por VARCHAR(255),
    ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE profissionais
    ADD COLUMN data_criacao TIMESTAMP,
    ADD COLUMN data_atualizacao TIMESTAMP,
    ADD COLUMN criado_por VARCHAR(255),
    ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE planos
    ADD COLUMN data_criacao TIMESTAMP,
    ADD COLUMN data_atualizacao TIMESTAMP,
    ADD COLUMN criado_por VARCHAR(255),
    ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE pagamentos
    ADD COLUMN data_criacao TIMESTAMP,
    ADD COLUMN data_atualizacao TIMESTAMP,
    ADD COLUMN criado_por VARCHAR(255),
    ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE aulas
    ADD COLUMN data_criacao TIMESTAMP,
    ADD COLUMN data_atualizacao TIMESTAMP,
    ADD COLUMN criado_por VARCHAR(255),
    ADD COLUMN alterado_por VARCHAR(255);
ALTER TABLE lista_espera
    ADD COLUMN data_criacao TIMESTAMP,
    ADD COLUMN data_atualizacao TIMESTAMP,
    ADD COLUMN criado_por VARCHAR(255),
    ADD COLUMN alterado_por VARCHAR(255);
