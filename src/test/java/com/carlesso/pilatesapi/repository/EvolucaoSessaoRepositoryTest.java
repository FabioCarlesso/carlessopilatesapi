package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Paciente inativo é ex-aluno, não registro apagado: o histórico clínico dele
 * continua legível. Ver issue #152.
 *
 * <p>Também cobre a listagem por paciente (issue #154): ordenação pela sessão e
 * isolamento entre pacientes.
 */
@PostgresDataJpaTest
class EvolucaoSessaoRepositoryTest extends PostgresTestcontainerSupport {

    @Autowired
    private EvolucaoSessaoRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findBySessaoId_quandoPacienteInativo_deveRetornarEvolucao() {
        EvolucaoSessao evolucao = persistirEvolucao("evolucao.inativo@email.com", false);
        Long sessaoId = evolucao.getSessao().getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findBySessaoId(sessaoId)).isPresent();
    }

    @Test
    void findByIdComSessao_quandoPacienteInativo_deveRetornarEvolucao() {
        EvolucaoSessao evolucao = persistirEvolucao("evolucao.id.inativo@email.com", false);
        Long id = evolucao.getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByIdComSessao(id)).isPresent();
    }

    @Test
    void findByPacienteOrdenadas_deveOrdenarDaSessaoMaisRecenteParaAMaisAntiga() {
        Paciente paciente = persistirPaciente("ordenacao.evolucao@email.com", true);
        Long manha = persistirEvolucao(paciente, LocalDate.of(2026, 5, 10), LocalTime.of(9, 0))
                .getSessao()
                .getId();
        Long tarde = persistirEvolucao(paciente, LocalDate.of(2026, 5, 10), LocalTime.of(14, 0))
                .getSessao()
                .getId();
        Long semHorario = persistirEvolucao(paciente, LocalDate.of(2026, 5, 10), null)
                .getSessao()
                .getId();
        Long diaAnterior = persistirEvolucao(paciente, LocalDate.of(2026, 5, 1), LocalTime.of(18, 0))
                .getSessao()
                .getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByPacienteOrdenadas(paciente.getId()))
                .extracting(evolucao -> evolucao.getSessao().getId())
                .containsExactly(tarde, manha, semHorario, diaAnterior);
    }

    @Test
    void findByPacienteOrdenadas_naoDeveRetornarEvolucoesDeOutroPaciente() {
        Paciente paciente = persistirPaciente("isolamento.evolucao@email.com", true);
        Paciente outro = persistirPaciente("isolamento.outro@email.com", true);
        Long sessaoDoPaciente = persistirEvolucao(paciente, LocalDate.of(2026, 5, 10), LocalTime.of(9, 0))
                .getSessao()
                .getId();
        persistirEvolucao(outro, LocalDate.of(2026, 5, 11), LocalTime.of(9, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByPacienteOrdenadas(paciente.getId()))
                .extracting(evolucao -> evolucao.getSessao().getId())
                .containsExactly(sessaoDoPaciente);
    }

    @Test
    void findByPacienteOrdenadas_quandoPacienteInativo_deveRetornarHistorico() {
        Paciente paciente = persistirPaciente("historico.evolucao.inativo@email.com", false);
        persistirEvolucao(paciente, LocalDate.of(2026, 5, 10), LocalTime.of(9, 0));
        persistirEvolucao(paciente, LocalDate.of(2026, 5, 11), LocalTime.of(9, 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByPacienteOrdenadas(paciente.getId())).hasSize(2);
    }

    @Test
    void findByPacienteOrdenadas_comPacienteSemEvolucoes_deveRetornarListaVazia() {
        Paciente paciente = persistirPaciente("sem.evolucao@email.com", true);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByPacienteOrdenadas(paciente.getId())).isEmpty();
    }

    private EvolucaoSessao persistirEvolucao(String email, boolean pacienteAtivo) {
        return persistirEvolucao(persistirPaciente(email, pacienteAtivo), LocalDate.of(2026, 5, 10), null);
    }

    private Paciente persistirPaciente(String email, boolean ativo) {
        Paciente paciente = new Paciente();
        paciente.setNome("Ana Oliveira");
        paciente.setEmail(email);
        // CPF único e curto (a coluna real é VARCHAR(14)); derivado do e-mail para
        // manter a unicidade entre os pacientes de teste, sem gerar valor negativo.
        paciente.setCpf(String.format("%011d", Integer.toUnsignedLong(email.hashCode()) % 100_000_000_000L));
        paciente.setAtivo(ativo);
        return entityManager.persist(paciente);
    }

    private EvolucaoSessao persistirEvolucao(Paciente paciente, LocalDate data, LocalTime horario) {
        SessaoPilates sessao = new SessaoPilates();
        sessao.setPaciente(paciente);
        sessao.setTipo(TipoSessao.PILATES);
        sessao.setStatus(StatusSessao.REALIZADA);
        sessao.setData(data);
        sessao.setHorario(horario);
        sessao = entityManager.persist(sessao);

        EvolucaoSessao evolucao = new EvolucaoSessao();
        evolucao.setSessao(sessao);
        evolucao.setDataHoraRegistro(LocalDateTime.of(2026, 5, 10, 10, 30));
        evolucao.setObservacoesFisioterapeuta("Paciente relatou melhora da dor lombar");
        return entityManager.persist(evolucao);
    }
}
