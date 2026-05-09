-- Admin inicial obrigatório para operação do sistema em produção.
-- Senha padrão: senha1234 — DEVE ser alterada no primeiro acesso.
INSERT INTO users (name, email, password, role, ativo)
VALUES ('Administrador', 'admin@carlessopilates.com', '$2a$10$c8n/Hy4BhKnuMQZoTY3IYOxdJikcivZdKytebSrQX4UqQMrAklgc6', 'ADMIN', TRUE)
ON CONFLICT (email) DO NOTHING;
