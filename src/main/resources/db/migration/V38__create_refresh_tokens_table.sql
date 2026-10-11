-- Refresh tokens rotativos (#97). Como em password_reset_tokens, só o hash SHA-256 é persistido.
--
-- family_id agrupa a cadeia de rotações nascida em um mesmo login: reapresentar um token já rotacionado
-- (used_at preenchido) é sinal de vazamento e revoga a família inteira. token_version guarda a versão do
-- usuário na emissão, para que a troca/redefinição de senha invalide também os refresh tokens.
-- family_expires_at é o teto absoluto da sessão, fixado no login e herdado por toda a cadeia: a rotação renova
-- expires_at, mas nunca além dele, então um token roubado não estende a sessão indefinidamente.
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    token_version BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    family_expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
-- Limpeza diária: remove as cadeias cujo teto absoluto já passou.
CREATE INDEX idx_refresh_tokens_family_expires_at ON refresh_tokens(family_expires_at);
