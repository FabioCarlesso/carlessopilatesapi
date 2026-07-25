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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Paciente inativo é ex-aluno, não registro apagado: o histórico clínico dele
 * continua legível. Ver issue #152.
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

    private EvolucaoSessao persistirEvolucao(String email, boolean pacienteAtivo) {
        Paciente paciente = new Paciente();
        paciente.setNome("Ana Oliveira");
        paciente.setEmail(email);
        // CPF único e curto (a coluna real é VARCHAR(14)); derivado do e-mail para
        // manter a unicidade entre os pacientes de teste, sem gerar valor negativo.
        paciente.setCpf(String.format("%011d", Integer.toUnsignedLong(email.hashCode()) % 100_000_000_000L));
        paciente.setAtivo(pacienteAtivo);
        paciente = entityManager.persist(paciente);

        SessaoPilates sessao = new SessaoPilates();
        sessao.setPaciente(paciente);
        sessao.setTipo(TipoSessao.PILATES);
        sessao.setStatus(StatusSessao.REALIZADA);
        sessao.setData(LocalDate.of(2026, 5, 10));
        sessao = entityManager.persist(sessao);

        EvolucaoSessao evolucao = new EvolucaoSessao();
        evolucao.setSessao(sessao);
        evolucao.setDataHoraRegistro(LocalDateTime.of(2026, 5, 10, 10, 30));
        evolucao.setObservacoesFisioterapeuta("Paciente relatou melhora da dor lombar");
        return entityManager.persist(evolucao);
    }
}
