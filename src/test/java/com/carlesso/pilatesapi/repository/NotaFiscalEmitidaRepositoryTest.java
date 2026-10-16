package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.NotaFiscalEmitida;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@PostgresDataJpaTest
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
                List.of(comNotaAnterior.getId(), semNotaAnterior.getId()), LocalDate.of(2026, 4, 1));

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
    void findByPacienteId_devolvePaginaNaOrdemPedidaEContaTodas() {
        Paciente paciente = entityManager.persist(paciente("Ana", "ana@email.com", "11122233344"));
        Paciente outro = entityManager.persist(paciente("Bia", "bia@email.com", "55566677788"));
        // 50 competências mensais, de jan/2022 a fev/2026, inseridas fora de ordem; a nota do outro paciente não conta.
        LocalDate primeira = LocalDate.of(2022, 1, 1);
        for (int i = 49; i >= 0; i -= 2) {
            entityManager.persist(nota(paciente, primeira.plusMonths(i)));
        }
        for (int i = 0; i < 50; i += 2) {
            entityManager.persist(nota(paciente, primeira.plusMonths(i)));
        }
        entityManager.persist(nota(outro, primeira));
        entityManager.flush();
        entityManager.clear();

        var pagina = repository.findByPacienteId(
                paciente.getId(), PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "competencia", "id")));

        assertThat(pagina.getTotalElements()).isEqualTo(50);
        assertThat(pagina.getTotalPages()).isEqualTo(5);
        assertThat(pagina.getContent())
                .extracting(NotaFiscalEmitida::getCompetencia)
                .containsExactly(
                        LocalDate.of(2025, 4, 1),
                        LocalDate.of(2025, 3, 1),
                        LocalDate.of(2025, 2, 1),
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2024, 12, 1),
                        LocalDate.of(2024, 11, 1),
                        LocalDate.of(2024, 10, 1),
                        LocalDate.of(2024, 9, 1),
                        LocalDate.of(2024, 8, 1),
                        LocalDate.of(2024, 7, 1));
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
