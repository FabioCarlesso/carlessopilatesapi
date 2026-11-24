-- Preferência do paciente para a NFSE (issue #229). Até aqui o relatório de NFSE exigia CPF de todo paciente que
-- pagou na competência e respondia 422 inteiro quando faltava um; e não havia onde registrar quem quer CPF na nota.
-- cpf_na_nota: o paciente pediu CPF/CNPJ na nota. Default FALSE, inclusive para os pacientes existentes — os
--   importados do seufisio não tinham essa informação, e o estúdio marca quem deseja.
-- nota_cpf_cnpj / nota_nome: tomador alternativo (responsável de aluno menor, empresa). Nulos = o próprio paciente.
--   O CPF/CNPJ é guardado só com dígitos (11 ou 14), validado no serviço.
ALTER TABLE pacientes ADD COLUMN cpf_na_nota BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pacientes ADD COLUMN nota_cpf_cnpj VARCHAR(14);
ALTER TABLE pacientes ADD COLUMN nota_nome VARCHAR(255);
