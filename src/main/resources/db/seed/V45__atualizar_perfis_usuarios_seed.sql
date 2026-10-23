-- A V44 leva todo USER para RECEPCAO; aqui os usuários de teste da V12 ganham o papel que o nome
-- de cada um já descrevia. A V12 não é editada para não quebrar o checksum de bancos dev existentes.
UPDATE users SET role = 'FINANCEIRO' WHERE email = 'financeiro@carlessopilates.com' AND role = 'RECEPCAO';
UPDATE users SET role = 'PROFISSIONAL' WHERE email = 'consulta@carlessopilates.com' AND role = 'RECEPCAO';
