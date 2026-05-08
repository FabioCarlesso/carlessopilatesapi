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
        SessaoPilates sessao = entityManager.persist(sessao(persistirPaciente("ana.repository@email.com", true)));
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

    @Test
    void transicionarStatusSeAgendada_paraCancelada_devePersistirDataAtualizacao() {
        SessaoPilates sessao = entityManager.persist(sessao(persistirPaciente("cancel.repository@email.com", true)));
        entityManager.flush();
        entityManager.clear();

        LocalDateTime quando = LocalDateTime.of(2026, 5, 8, 12, 30);
        int atualizadas = repository.transicionarStatusSeAgendada(
                sessao.getId(), StatusSessao.CANCELADA, quando);

        assertThat(atualizadas).isEqualTo(1);
        assertThat(repository.findByIdComPaciente(sessao.getId()))
                .get()
                .satisfies(s -> {
                    assertThat(s.getStatus()).isEqualTo(StatusSessao.CANCELADA);
                    assertThat(s.getDataAtualizacao()).isEqualTo(quando);
                });
    }

    @Test
    void transicionarStatusSeAgendada_comIdInexistente_deveRetornarZero() {
        int atualizadas = repository.transicionarStatusSeAgendada(
                999_999L, StatusSessao.REALIZADA, LocalDateTime.of(2026, 5, 8, 12, 0));

        assertThat(atualizadas).isZero();
    }

    @Test
    void findByIdComPaciente_quandoPacienteInativo_deveRetornarVazio() {
        SessaoPilates sessao = entityManager.persist(
                sessao(persistirPaciente("inativo.repository@email.com", false)));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByIdComPaciente(sessao.getId())).isEmpty();
    }

    private Paciente persistirPaciente(String email, boolean ativo) {
        Paciente paciente = new Paciente();
        paciente.setNome("Ana Oliveira");
        paciente.setEmail(email);
        paciente.setCpf("cpf-" + email);
        paciente.setAtivo(ativo);
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
