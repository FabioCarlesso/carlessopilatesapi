CREATE TABLE preferencias_usuario (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    idioma VARCHAR(20) NOT NULL,
    tema VARCHAR(20) NOT NULL,
    notificacoes_email BOOLEAN NOT NULL,
    notificacoes_push BOOLEAN NOT NULL
);
