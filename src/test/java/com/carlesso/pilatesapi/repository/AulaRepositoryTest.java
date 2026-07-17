package com.carlesso.pilatesapi.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@PostgresDataJpaTest
class AulaRepositoryTest extends PostgresTestcontainerSupport {

    @Autowired
    private AulaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Paciente pacienteAtivo;
    private Paciente pacienteInativo;
    private Pagamento pagamentoAtivo;
    private Pagamento pagamentoInativo;
    private Profissional profissional;
    private Aula aulaAtiva;
    private Aula aulaInativa;

    @BeforeEach
    void setUp() {
        pacienteAtivo = entityManager.persist(paciente("Ana Ativa", "ana.ativa@email.com", "11122233344", true));
        pacienteInativo = entityManager.persist(paciente("Bia Inativa", "bia.inativa@email.com", "55566677788", false));
        profissional = entityManager.persist(profissional());

        Plano planoAtivo = entityManager.persist(plano(pacienteAtivo));
        Plano planoInativo = entityManager.persist(plano(pacienteInativo));

        pagamentoAtivo = entityManager.persist(pagamento(pacienteAtivo, planoAtivo, LocalDate.of(2025, 2, 1)));
        pagamentoInativo = entityManager.persist(pagamento(pacienteInativo, planoInativo, LocalDate.of(2025, 3, 1)));

        aulaAtiva = entityManager.persist(aula(pacienteAtivo, pagamentoAtivo, LocalDate.of(2025, 2, 3)));
        aulaInativa = entityManager.persist(aula(pacienteInativo, pagamentoInativo, LocalDate.of(2025, 3, 3)));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findByIdAndPacienteAtivoTrue_naoRetornaAulaDePacienteInativo() {
        assertThat(repository.findByIdAndPacienteAtivoTrue(aulaAtiva.getId())).isPresent();
        assertThat(repository.findByIdAndPacienteAtivoTrue(aulaInativa.getId())).isEmpty();
    }

    @Test
    void findByPacienteIdOrderByData_naoRetornaAulasDePacienteInativo() {
        assertThat(repository.findByPacienteIdOrderByData(pacienteAtivo.getId()))
                .extracting(aula -> aula.getPaciente().getNome())
                .containsExactly("Ana Ativa");

        assertThat(repository.findByPacienteIdOrderByData(pacienteInativo.getId()))
                .isEmpty();
    }

    @Test
    void findByPagamentoIdOrderByData_naoRetornaAulasQuandoPacienteDoPagamentoEstaInativo() {
        assertThat(repository.findByPagamentoIdOrderByData(pagamentoAtivo.getId()))
                .extracting(Aula::getId)
                .containsExactly(aulaAtiva.getId());

        assertThat(repository.findByPagamentoIdOrderByData(pagamentoInativo.getId()))
                .isEmpty();
    }

    @Test
    void findByProfissionalIdAndRealizadaTrueAndDataBetweenOrderByData_naoRetornaPacienteInativo() {
        var aulas = repository.findByProfissionalIdAndRealizadaTrueAndDataBetweenOrderByData(
                profissional.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertThat(aulas).extracting(aula -> aula.getPaciente().getNome()).containsExactly("Ana Ativa");
    }

    @Test
    void findRelatorioPagamentoByProfissionalIdAndPeriodo_retornaCamposEQuantidadeNaMesmaConsulta() {
        entityManager.persist(
                aulaNaoRealizadaSemProfissional(pacienteAtivo, pagamentoAtivo, LocalDate.of(2025, 2, 10)));
        entityManager.persist(
                aulaNaoRealizadaSemProfissional(pacienteAtivo, pagamentoAtivo, LocalDate.of(2025, 2, 17)));
        entityManager.flush();
        entityManager.clear();

        var aulas = repository.findRelatorioPagamentoByProfissionalIdAndPeriodo(
                profissional.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        assertThat(aulas).singleElement().satisfies(aula -> {
            assertThat(aula.getAulaId()).isEqualTo(aulaAtiva.getId());
            assertThat(aula.getPacienteNome()).isEqualTo("Ana Ativa");
            assertThat(aula.getPagamentoId()).isEqualTo(pagamentoAtivo.getId());
            assertThat(aula.getValorPagamento()).isEqualByComparingTo("200.00");
            assertThat(aula.getQuantidadeAulasPagamento()).isEqualTo(3L);
        });
    }

    @Test
    void countGroupedByPagamentoId_naoContaAulasDePacienteInativo() {
        var contagens = repository.countGroupedByPagamentoId(List.of(pagamentoAtivo.getId(), pagamentoInativo.getId()));

        assertThat(contagens).singleElement().satisfies(row -> {
            assertThat(row[0]).isEqualTo(pagamentoAtivo.getId());
            assertThat(row[1]).isEqualTo(1L);
        });
    }

    private Paciente paciente(String nome, String email, String cpf, boolean ativo) {
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail(email);
        paciente.setCpf(cpf);
        paciente.setAtivo(ativo);
        return paciente;
    }

    private Profissional profissional() {
        Profissional profissional = new Profissional();
        profissional.setNome("Paula Mendes");
        profissional.setEmail("paula@email.com");
        profissional.setCpf("12345678900");
        profissional.setTipoContrato(TipoContrato.PJ);
        profissional.setPercentualPagamentoAula(new BigDecimal("45.00"));
        profissional.setDataInicio(LocalDate.of(2024, 1, 15));
        return profissional;
    }

    private Plano plano(Paciente paciente) {
        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("200.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY));
        plano.setDataInicio(LocalDate.of(2025, 1, 1));
        return plano;
    }

    private Pagamento pagamento(Paciente paciente, Plano plano, LocalDate periodoInicio) {
        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal("200.00"));
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setDataVencimento(periodoInicio.plusDays(10));
        pagamento.setPeriodoInicio(periodoInicio);
        pagamento.setPeriodoFim(periodoInicio.plusMonths(1).minusDays(1));
        return pagamento;
    }

    private Aula aula(Paciente paciente, Pagamento pagamento, LocalDate data) {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamento);
        aula.setProfissional(profissional);
        aula.setData(data);
        aula.setRealizada(true);
        return aula;
    }

    private Aula aulaNaoRealizadaSemProfissional(Paciente paciente, Pagamento pagamento, LocalDate data) {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamento);
        aula.setData(data);
        aula.setRealizada(false);
        return aula;
    }
}
