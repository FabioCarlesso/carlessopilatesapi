ALTER TABLE profissionais ALTER COLUMN percentual_pagamento_aula TYPE NUMERIC(5,2);

INSERT INTO profissionais (nome, email, cpf, telefone, tipo_contrato, percentual_pagamento_aula, data_inicio, ativo) VALUES
('Paula Mendes', 'paula.mendes@carlessopilates.com', '123.456.111-00', '(11) 98888-1111', 'PJ', 45.00, '2024-01-15', TRUE),
('Ricardo Souza', 'ricardo.souza@carlessopilates.com', '123.456.222-00', '(11) 97777-2222', 'AUTONOMO', 40.00, '2024-03-01', TRUE),
('Fernanda Lima', 'fernanda.lima@carlessopilates.com', '123.456.333-00', '(11) 96666-3333', 'CLT', 35.00, '2024-02-10', TRUE);
