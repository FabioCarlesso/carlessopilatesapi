package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class SessaoPilatesRepositoryTest {

    @Autowired
    private SessaoPilatesRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void transicionarStatusSeAgendada_deveAtualizarSomenteUmaVez() {
        SessaoPilates sessao = entityManager.persist(sessao(paciente()));
        entityManager.flush();
        entityManager.clear();

        int primeiraAtualizacao = repository.transicionarStatusSeAgendada(
                sessao.getId(), StatusSessao.REALIZADA, LocalDateTime.of(2026, 5, 8, 12, 0));
        int segundaAtualizacao = repository.transicionarStatusSeAgendada(
                sessao.getId(), StatusSessao.CANCELADA, LocalDateTime.of(2026, 5, 8, 12, 1));

        assertThat(primeiraAtualizacao).isEqualTo(1);
        assertThat(segundaAtualizacao).isZero();
        assertThat(repository.findByIdComPaciente(sessao.getId()))
                .get()
                .extracting(SessaoPilates::getStatus)
                .isEqualTo(StatusSessao.REALIZADA);
    }

    private Paciente paciente() {
        Paciente paciente = new Paciente();
        paciente.setNome("Ana Oliveira");
        paciente.setEmail("ana.repository@email.com");
        paciente.setCpf("12345678900");
        return entityManager.persist(paciente);
    }

    private SessaoPilates sessao(Paciente paciente) {
        SessaoPilates sessao = new SessaoPilates();
        sessao.setPaciente(paciente);
        sessao.setTipo(TipoSessao.PILATES);
        sessao.setData(LocalDate.of(2026, 5, 10));
        return sessao;
    }
}
