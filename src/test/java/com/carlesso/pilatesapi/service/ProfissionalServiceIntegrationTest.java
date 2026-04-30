package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@Import(ProfissionalService.class)
class ProfissionalServiceIntegrationTest {

    @Autowired
    private ProfissionalRepository repository;

    @Autowired
    private ProfissionalService service;

    @Autowired
    private TestEntityManager entityManager;

    private Profissional profissionalSalvo;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.save(profissional("Paula Mendes", "paula@email.com", "12345678900", TipoContrato.PJ, "45.00", true));
        repository.save(profissional("Ricardo Souza", "ricardo@email.com", "98765432100", TipoContrato.AUTONOMO, "40.00", true));
        repository.save(profissional("Fernanda Lima", "fernanda@email.com", "11122233344", TipoContrato.CLT, "35.00", false));
        profissionalSalvo = repository.findAll().stream()
                .filter(p -> "paula@email.com".equals(p.getEmail()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void listar_semStatusRetornaAtivosPorPadrao() {
        var resultado = service.listar(null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(resultado.getContent())
                .extracting("nome")
                .containsExactlyInAnyOrder("Paula Mendes", "Ricardo Souza");
    }

    @Test
    void listar_filtraPorCamposDaIssueEStatus() {
        var resultado = service.listar(
                "fer",
                "email.com",
                TipoContrato.CLT,
                new BigDecimal("35.00"),
                false,
                PageRequest.of(0, 10));

        assertThat(resultado.getContent())
                .singleElement()
                .satisfies(profissional -> {
                    assertThat(profissional.nome()).isEqualTo("Fernanda Lima");
                    assertThat(profissional.ativo()).isFalse();
                });
    }

    @Test
    void listar_filtroNomeEmBranco_deveIgnorarFiltro() {
        var resultado = service.listar("   ", null, null, null, null, PageRequest.of(0, 10));

        assertThat(resultado.getContent())
                .extracting("nome")
                .containsExactlyInAnyOrder("Paula Mendes", "Ricardo Souza");
    }

    @Test
    void listar_semAtivoNaoRetornaTodosProfissionais() {
        var ativos = service.listar(null, null, null, null, null, PageRequest.of(0, 10));
        var inativos = service.listar(null, null, null, null, false, PageRequest.of(0, 10));

        assertThat(ativos.getTotalElements()).isEqualTo(2);
        assertThat(inativos.getTotalElements()).isEqualTo(1);
        assertThat(ativos.getTotalElements() + inativos.getTotalElements())
                .isEqualTo(3);
    }

    @Test
    void listar_filtraPorPercentualPagamentoAulaIsolado() {
        var resultado = service.listar(null, null, null, new BigDecimal("40.00"), null, PageRequest.of(0, 10));

        assertThat(resultado.getContent())
                .singleElement()
                .satisfies(profissional -> {
                    assertThat(profissional.nome()).isEqualTo("Ricardo Souza");
                    assertThat(profissional.percentualPagamentoAula()).isEqualByComparingTo("40.00");
                });
    }

    @Test
    void gerarRelatorioPagamento_retornaAulasRealizadasDoProfissionalNoPeriodo() {
        Paciente paciente = entityManager.persist(paciente("Ana Souza", "ana@email.com", "44455566677"));
        Plano plano = entityManager.persist(plano(paciente));
        Pagamento pagamento = entityManager.persist(pagamento(paciente, plano,
                LocalDate.of(2025, 2, 1), new BigDecimal("200.00")));
        entityManager.persist(aula(paciente, pagamento, profissionalSalvo, LocalDate.of(2025, 2, 3)));
        entityManager.persist(aula(paciente, pagamento, profissionalSalvo, LocalDate.of(2025, 2, 5)));
        entityManager.flush();
        entityManager.clear();

        var relatorio = service.gerarRelatorioPagamento(
                profissionalSalvo.getId(),
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28));

        assertThat(relatorio.profissional().id()).isEqualTo(profissionalSalvo.getId());
        assertThat(relatorio.profissional().nome()).isEqualTo("Paula Mendes");
        assertThat(relatorio.resumo().totalAulas()).isEqualTo(2);
        assertThat(relatorio.resumo().quantidadePagamentos()).isEqualTo(1);
        assertThat(relatorio.resumo().totalPagamentosBruto()).isEqualByComparingTo("200.00");
        assertThat(relatorio.resumo().totalProfissional()).isEqualByComparingTo("90.00");
        assertThat(relatorio.aulas()).hasSize(2);
        assertThat(relatorio.aulas().get(0).valorBaseAula()).isEqualByComparingTo("100.00");
        assertThat(relatorio.aulas().get(0).valorProfissional()).isEqualByComparingTo("45.00");
        assertThat(relatorio.pagamentos()).hasSize(1);
        assertThat(relatorio.pagamentos().get(0).quantidadeAulasNoPeriodo()).isEqualTo(2);
        assertThat(relatorio.pagamentos().get(0).totalProfissional()).isEqualByComparingTo("90.00");
    }

    @Test
    void gerarRelatorioPagamento_ignoraAulasDePacienteInativo() {
        Paciente ativo = entityManager.persist(paciente("Ana Ativa", "ana.ativa@email.com", "11100011100"));
        Paciente inativo = entityManager.persist(pacienteInativo("Bia Inativa", "bia.inativa@email.com", "22200022200"));
        Plano planoAtivo = entityManager.persist(plano(ativo));
        Plano planoInativo = entityManager.persist(plano(inativo));
        Pagamento pagAtivo = entityManager.persist(pagamento(ativo, planoAtivo,
                LocalDate.of(2025, 2, 1), new BigDecimal("200.00")));
        Pagamento pagInativo = entityManager.persist(pagamento(inativo, planoInativo,
                LocalDate.of(2025, 2, 1), new BigDecimal("200.00")));
        entityManager.persist(aula(ativo, pagAtivo, profissionalSalvo, LocalDate.of(2025, 2, 3)));
        entityManager.persist(aula(inativo, pagInativo, profissionalSalvo, LocalDate.of(2025, 2, 5)));
        entityManager.flush();
        entityManager.clear();

        var relatorio = service.gerarRelatorioPagamento(
                profissionalSalvo.getId(),
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 28));

        assertThat(relatorio.resumo().totalAulas()).isEqualTo(1);
        assertThat(relatorio.aulas()).extracting(a -> a.pacienteId())
                .containsExactly(ativo.getId());
    }

    @Test
    void gerarRelatorioPagamento_periodoInvalido_lancaExcecao() {
        assertThatThrownBy(() -> service.gerarRelatorioPagamento(
                profissionalSalvo.getId(),
                LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pode ser maior");
    }

    private Profissional profissional(
            String nome,
            String email,
            String cpf,
            TipoContrato tipoContrato,
            String percentualPagamentoAula,
            boolean ativo) {
        Profissional profissional = new Profissional();
        profissional.setNome(nome);
        profissional.setEmail(email);
        profissional.setCpf(cpf);
        profissional.setTelefone("11999999999");
        profissional.setTipoContrato(tipoContrato);
        profissional.setPercentualPagamentoAula(new BigDecimal(percentualPagamentoAula));
        profissional.setDataInicio(LocalDate.of(2024, 1, 15));
        profissional.setAtivo(ativo);
        return profissional;
    }

    private Paciente paciente(String nome, String email, String cpf) {
        Paciente p = new Paciente();
        p.setNome(nome);
        p.setEmail(email);
        p.setCpf(cpf);
        p.setAtivo(true);
        return p;
    }

    private Paciente pacienteInativo(String nome, String email, String cpf) {
        Paciente p = paciente(nome, email, cpf);
        p.setAtivo(false);
        return p;
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

    private Pagamento pagamento(Paciente paciente, Plano plano, LocalDate periodoInicio, BigDecimal valor) {
        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(valor);
        pagamento.setStatus(StatusPagamento.PAGO);
        pagamento.setDataVencimento(periodoInicio.plusDays(10));
        pagamento.setPeriodoInicio(periodoInicio);
        pagamento.setPeriodoFim(periodoInicio.plusMonths(1).minusDays(1));
        return pagamento;
    }

    private Aula aula(Paciente paciente, Pagamento pagamento, Profissional profissional, LocalDate data) {
        Aula aula = new Aula();
        aula.setPaciente(paciente);
        aula.setPagamento(pagamento);
        aula.setProfissional(profissional);
        aula.setData(data);
        aula.setRealizada(true);
        return aula;
    }
}
