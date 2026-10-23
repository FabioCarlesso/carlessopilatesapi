-- O papel genérico USER deixou de existir (#131). RECEPCAO é o papel de equipe com menos privilégio
-- (sem acesso a prontuário): o admin promove depois quem for PROFISSIONAL ou FINANCEIRO.
UPDATE users SET role = 'RECEPCAO' WHERE role = 'USER';
