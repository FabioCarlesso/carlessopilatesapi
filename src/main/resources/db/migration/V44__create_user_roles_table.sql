-- Papéis de negócio (#131). Um usuário pode acumular papéis — a fisioterapeuta que também cuida do
-- financeiro é PROFISSIONAL e FINANCEIRO —, então o papel sai da coluna users.role para user_roles.
-- O USER genérico deixou de existir e vira RECEPCAO, o papel de equipe com menos privilégio (sem
-- acesso a prontuário): o admin acrescenta depois PROFISSIONAL ou FINANCEIRO a quem for o caso.
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

INSERT INTO user_roles (user_id, role)
SELECT id, CASE WHEN role = 'USER' THEN 'RECEPCAO' ELSE role END FROM users;

ALTER TABLE users DROP COLUMN role;
