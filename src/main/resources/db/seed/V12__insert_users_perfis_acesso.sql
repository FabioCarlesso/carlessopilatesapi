INSERT INTO users (name, email, password, role) VALUES
    ('Administrador Principal', 'admin@carlessopilates.com', '$2a$10$c8n/Hy4BhKnuMQZoTY3IYOxdJikcivZdKytebSrQX4UqQMrAklgc6', 'ADMIN'),
    ('Administrador Operacional', 'operacional@carlessopilates.com', '$2a$10$c8n/Hy4BhKnuMQZoTY3IYOxdJikcivZdKytebSrQX4UqQMrAklgc6', 'ADMIN'),
    ('Recepção Carlesso', 'recepcao@carlessopilates.com', '$2a$10$c8n/Hy4BhKnuMQZoTY3IYOxdJikcivZdKytebSrQX4UqQMrAklgc6', 'USER'),
    ('Financeiro Carlesso', 'financeiro@carlessopilates.com', '$2a$10$c8n/Hy4BhKnuMQZoTY3IYOxdJikcivZdKytebSrQX4UqQMrAklgc6', 'USER'),
    ('Consulta Carlesso', 'consulta@carlessopilates.com', '$2a$10$c8n/Hy4BhKnuMQZoTY3IYOxdJikcivZdKytebSrQX4UqQMrAklgc6', 'USER')
ON CONFLICT (email) DO NOTHING;
