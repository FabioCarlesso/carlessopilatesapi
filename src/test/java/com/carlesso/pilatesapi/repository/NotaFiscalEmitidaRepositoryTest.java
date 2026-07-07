package com.carlesso.pilatesapi.repository;

import com.carlesso.pilatesapi.entity.NotaFiscalEmitida;
import com.carlesso.pilatesapi.entity.Paciente;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotaFiscalEmitidaRepositoryTest extends PostgresTestcontainerSupport {

    @Autowired
    NotaFiscalEmitidaRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void findPacienteIdsComNotaEmitidaAntes_retornaApenasPacientesComNotaEmCompetenciaAnterior() {
        Paciente comNotaAnterior = entityManager.persist(paciente("Ana", "ana@email.com", "11122233344"));
        Paciente semNotaAnterior = entityManager.persist(paciente("Bia", "bia@email.com", "55566677788"));

        entityManager.persist(nota(comNotaAnterior, LocalDate.of(2026, 3, 1)));
        entityManager.persist(nota(semNotaAnterior, LocalDate.of(2026, 4, 1)));
        entityManager.flush();

        var pacienteIds = repository.findPacienteIdsComNotaEmitidaAntes(
                List.of(comNotaAnterior.getId(), semNotaAnterior.getId()),
                LocalDate.of(2026, 4, 1));

        assertThat(pacienteIds).containsExactly(comNotaAnterior.getId());
    }

    @Test
    void findByPacienteIdAndCompetencia_retornaNotaDaCompetencia() {
        Paciente paciente = entityManager.persist(paciente("Ana", "ana@email.com", "11122233344"));
        entityManager.persist(nota(paciente, LocalDate.of(2026, 4, 1)));
        entityManager.flush();
        entityManager.clear();

        var nota = repository.findByPacienteIdAndCompetencia(paciente.getId(), LocalDate.of(2026, 4, 1));

        assertThat(nota).isPresent();
        assertThat(nota.get().getNumeroNota()).isEqualTo("NF-1");
        // dataCriacao é preenchida automaticamente pelo callback @PrePersist (helper não a define).
        assertThat(nota.get().getDataCriacao()).isNotNull();
    }

    @Test
    void findByPacienteIdOrderByCompetenciaDesc_retornaDaMaisRecenteParaMaisAntiga() {
        Paciente paciente = entityManager.persist(paciente("Ana", "ana@email.com", "11122233344"));
        entityManager.persist(nota(paciente, LocalDate.of(2026, 3, 1)));
        entityManager.persist(nota(paciente, LocalDate.of(2026, 5, 1)));
        entityManager.persist(nota(paciente, LocalDate.of(2026, 4, 1)));
        entityManager.flush();
        entityManager.clear();

        var notas = repository.findByPacienteIdOrderByCompetenciaDesc(paciente.getId());

        assertThat(notas)
                .extracting(NotaFiscalEmitida::getCompetencia)
                .containsExactly(
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 3, 1));
    }

    private Paciente paciente(String nome, String email, String cpf) {
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail(email);
        paciente.setCpf(cpf);
        paciente.setAtivo(true);
        return paciente;
    }

    private NotaFiscalEmitida nota(Paciente paciente, LocalDate competencia) {
        NotaFiscalEmitida nota = new NotaFiscalEmitida();
        nota.setPaciente(paciente);
        nota.setCompetencia(competencia);
        nota.setNumeroNota("NF-1");
        nota.setDataEmissao(competencia.plusDays(10));
        nota.setValor(new BigDecimal("250.00"));
        return nota;
    }
}
