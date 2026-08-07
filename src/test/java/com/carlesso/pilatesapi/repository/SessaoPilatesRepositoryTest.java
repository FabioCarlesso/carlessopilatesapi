package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.dto.SessaoPilatesResponseDTO;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresDataJpaTest
class SessaoPilatesRepositoryTest extends PostgresTestcontainerSupport {

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
        int atualizadas = repository.transicionarStatusSeAgendada(sessao.getId(), StatusSessao.CANCELADA, quando);

        assertThat(atualizadas).isEqualTo(1);
        assertThat(repository.findByIdComPaciente(sessao.getId())).get().satisfies(s -> {
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
    void findByIdComPaciente_quandoPacienteInativo_deveRetornarSessao() {
        SessaoPilates sessao = entityManager.persist(sessao(persistirPaciente("inativo.repository@email.com", false)));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByIdComPaciente(sessao.getId())).isPresent();
    }

    @Test
    void findByPacienteOrdenadas_quandoPacienteInativo_deveRetornarHistorico() {
        Paciente paciente = persistirPaciente("historico.repository@email.com", false);
        entityManager.persist(sessao(paciente));
        entityManager.persist(sessao(paciente));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByPacienteOrdenadas(paciente.getId())).hasSize(2);
    }

    @Test
    void findAgendaPorPeriodo_semFiltrosOpcionais_incluiSessaoDePacienteInativo() {
        Paciente ativo = persistirPaciente("agenda.ativo@email.com", true);
        Paciente inativo = persistirPaciente("agenda.inativo@email.com", false);
        SessaoPilates doAtivo = entityManager.persist(sessao(ativo, LocalDate.of(2026, 6, 1), LocalTime.of(9, 0)));
        SessaoPilates doInativo = entityManager.persist(sessao(inativo, LocalDate.of(2026, 6, 2), LocalTime.of(10, 0)));
        entityManager.flush();
        entityManager.clear();

        var sessoes = repository.findAgendaPorPeriodo(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, null, null);

        assertThat(sessoes).extracting(SessaoPilates::getId).containsExactly(doAtivo.getId(), doInativo.getId());
    }

    @Test
    void findAgendaPorPeriodo_ordenaPorDataHorarioEId_comSessaoSemHorarioNoFimDoDia() {
        Paciente paciente = persistirPaciente("agenda.ordem@email.com", true);
        SessaoPilates semHorario = entityManager.persist(sessao(paciente, LocalDate.of(2026, 6, 1), null));
        SessaoPilates tarde = entityManager.persist(sessao(paciente, LocalDate.of(2026, 6, 1), LocalTime.of(16, 0)));
        SessaoPilates manha = entityManager.persist(sessao(paciente, LocalDate.of(2026, 6, 1), LocalTime.of(8, 0)));
        SessaoPilates outroDia = entityManager.persist(sessao(paciente, LocalDate.of(2026, 6, 2), LocalTime.of(7, 0)));
        entityManager.flush();
        entityManager.clear();

        var sessoes = repository.findAgendaPorPeriodo(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, null, null);

        assertThat(sessoes)
                .extracting(SessaoPilates::getId)
                .containsExactly(manha.getId(), tarde.getId(), semHorario.getId(), outroDia.getId());
    }

    @Test
    void findAgendaPorPeriodo_foraDoIntervalo_retornaVazio() {
        Paciente paciente = persistirPaciente("agenda.fora@email.com", true);
        entityManager.persist(sessao(paciente, LocalDate.of(2026, 6, 1), LocalTime.of(9, 0)));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAgendaPorPeriodo(
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null, null, null, null))
                .isEmpty();
    }

    @Test
    void findAgendaPorPeriodo_comProfissionalEPaciente_restringeResultado() {
        Paciente paciente = persistirPaciente("agenda.prof@email.com", true);
        Paciente outroPaciente = persistirPaciente("agenda.outro@email.com", true);
        Profissional profissional = entityManager.persist(profissional("agenda.prof.profissional@email.com"));

        SessaoPilates comProfissional = sessao(paciente, LocalDate.of(2026, 6, 1), LocalTime.of(9, 0));
        comProfissional.setProfissional(profissional);
        entityManager.persist(comProfissional);
        entityManager.persist(sessao(outroPaciente, LocalDate.of(2026, 6, 2), LocalTime.of(9, 0)));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAgendaPorPeriodo(
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), profissional.getId(), null, null, null))
                .extracting(SessaoPilates::getId)
                .containsExactly(comProfissional.getId());

        assertThat(repository.findAgendaPorPeriodo(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        profissional.getId(),
                        outroPaciente.getId(),
                        null,
                        null))
                .isEmpty();
    }

    @Test
    void findAgendaPorPeriodo_comTipoEStatus_restringeResultado() {
        Paciente paciente = persistirPaciente("agenda.tipo@email.com", true);
        SessaoPilates pilatesAgendada = entityManager.persist(sessao(paciente, LocalDate.of(2026, 6, 1), null));

        SessaoPilates fisioCancelada = sessao(paciente, LocalDate.of(2026, 6, 2), null);
        fisioCancelada.setTipo(TipoSessao.FISIOTERAPIA);
        fisioCancelada.setStatus(StatusSessao.CANCELADA);
        entityManager.persist(fisioCancelada);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAgendaPorPeriodo(
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, TipoSessao.PILATES, null))
                .extracting(SessaoPilates::getId)
                .containsExactly(pilatesAgendada.getId());

        assertThat(repository.findAgendaPorPeriodo(
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, null, StatusSessao.CANCELADA))
                .extracting(SessaoPilates::getId)
                .containsExactly(fisioCancelada.getId());

        assertThat(repository.findAgendaPorPeriodo(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 30),
                        null,
                        null,
                        TipoSessao.PILATES,
                        StatusSessao.CANCELADA))
                .isEmpty();
    }

    @Test
    void findAgendaPorPeriodo_naoGeraConsultaPorLinha() {
        Paciente paciente = persistirPaciente("agenda.nmais1@email.com", true);
        Profissional profissional = entityManager.persist(profissional("agenda.nmais1.profissional@email.com"));
        for (int dia = 1; dia <= 3; dia++) {
            SessaoPilates sessao = sessao(paciente, LocalDate.of(2026, 6, dia), LocalTime.of(9, 0));
            sessao.setProfissional(profissional);
            entityManager.persist(sessao);
        }
        entityManager.flush();
        entityManager.clear();

        Statistics estatisticas = entityManager
                .getEntityManager()
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        estatisticas.setStatisticsEnabled(true);
        estatisticas.clear();

        var sessoes = repository.findAgendaPorPeriodo(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, null, null);
        sessoes.forEach(sessao -> SessaoPilatesResponseDTO.from(sessao).nomeProfissional());

        assertThat(sessoes).hasSize(3);
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
    }

    private Profissional profissional(String email) {
        Profissional profissional = new Profissional();
        profissional.setNome("Paula Mendes");
        profissional.setEmail(email);
        profissional.setCpf(String.format("%011d", Integer.toUnsignedLong(email.hashCode()) % 100_000_000_000L));
        profissional.setTipoContrato(TipoContrato.PJ);
        profissional.setPercentualPagamentoAula(new BigDecimal("45.00"));
        profissional.setDataInicio(LocalDate.of(2024, 1, 15));
        return profissional;
    }

    private SessaoPilates sessao(Paciente paciente, LocalDate data, LocalTime horario) {
        SessaoPilates sessao = sessao(paciente);
        sessao.setData(data);
        sessao.setHorario(horario);
        return sessao;
    }

    private Paciente persistirPaciente(String email, boolean ativo) {
        Paciente paciente = new Paciente();
        paciente.setNome("Ana Oliveira");
        paciente.setEmail(email);
        // CPF único e curto (a coluna real é VARCHAR(14)); derivado do e-mail
        // para manter a unicidade entre os pacientes de teste. Usa aritmética sem
        // sinal para nunca gerar valor negativo (evita a armadilha de Math.abs).
        paciente.setCpf(String.format("%011d", Integer.toUnsignedLong(email.hashCode()) % 100_000_000_000L));
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
