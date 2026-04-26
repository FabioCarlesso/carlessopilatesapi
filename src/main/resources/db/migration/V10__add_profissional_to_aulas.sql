ALTER TABLE aulas
    ADD COLUMN profissional_id BIGINT REFERENCES profissionais(id);
