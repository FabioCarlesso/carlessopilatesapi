package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre o índice parcial de unicidade da migration V23
 * ({@code CREATE UNIQUE INDEX ... WHERE email/cpf IS NOT NULL}), que só existe no
 * schema PostgreSQL criado pelo Flyway — por isso este teste roda contra o
 * container ({@link PostgresTestcontainerSupport}) e não no H2.
 */
@PostgresDataJpaTest
class PacienteRepositoryTest extends PostgresTestcontainerSupport {

    @Autowired
    PacienteRepository repository;

    @Test
    void permiteMultiplosPacientesComEmailNulo() {
        repository.saveAndFlush(paciente("Ana", null, "11111111111"));
        repository.saveAndFlush(paciente("Bia", null, "22222222222"));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void permiteMultiplosPacientesComCpfNulo() {
        repository.saveAndFlush(paciente("Ana", "ana@email.com", null));
        repository.saveAndFlush(paciente("Bia", "bia@email.com", null));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void rejeitaEmailDuplicadoPreenchido() {
        repository.saveAndFlush(paciente("Ana", "duplicado@email.com", "11111111111"));

        assertThatThrownBy(() ->
                repository.saveAndFlush(paciente("Bia", "duplicado@email.com", "22222222222")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejeitaCpfDuplicadoPreenchido() {
        repository.saveAndFlush(paciente("Ana", "ana@email.com", "99999999999"));

        assertThatThrownBy(() ->
                repository.saveAndFlush(paciente("Bia", "bia@email.com", "99999999999")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Paciente paciente(String nome, String email, String cpf) {
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail(email);
        paciente.setCpf(cpf);
        paciente.setAtivo(true);
        return paciente;
    }
}
