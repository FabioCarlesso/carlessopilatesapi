-- A V44 leva todo USER para RECEPCAO; aqui os usuários de teste da V12 ganham o papel que o nome
-- de cada um já descrevia. A V12 não é editada para não quebrar o checksum de bancos dev existentes.
UPDATE user_roles SET role = 'FINANCEIRO'
WHERE role = 'RECEPCAO' AND user_id = (SELECT id FROM users WHERE email = 'financeiro@carlessopilates.com');
UPDATE user_roles SET role = 'PROFISSIONAL'
WHERE role = 'RECEPCAO' AND user_id = (SELECT id FROM users WHERE email = 'consulta@carlessopilates.com');
