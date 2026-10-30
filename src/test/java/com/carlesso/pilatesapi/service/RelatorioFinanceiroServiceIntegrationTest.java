package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.carlesso.pilatesapi.dto.RelatorioFinanceiroDTO;
import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * Relatório financeiro (#108) contra o PostgreSQL, com massa conhecida: os totais precisam bater com a soma dos
 * relatórios individuais por profissional no mesmo período.
 */
@PostgresDataJpaTest
@Import(ProfissionalService.class)
class RelatorioFinanceiroServiceIntegrationTest extends PostgresTestcontainerSupport {

    private static final LocalDate INICIO = LocalDate.of(2026, 2, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 2, 28);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 2, 20);

    @Autowired
    PagamentoRepository pagamentoRepository;

    @Autowired
    AulaRepository aulaRepository;

    @Autowired
    ProfissionalService profissionalService;

    @Autowired
    TestEntityManager entityManager;

    RelatorioFinanceiroService service;

    private int sequencial;
    private Profissional paula;
    private Profissional ana;
    private Profissional bruno;
    private Profissional carla;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(HOJE.atTime(10, 0).atZone(FUSO).toInstant(), FUSO);
        service = new RelatorioFinanceiroService(pagamentoRepository, aulaRepository, profissionalService, relogio);
    }

    @Test
    void gerar_totaisBatemComOsRelatoriosIndividuais() {
        montarMassa();

        RelatorioFinanceiroDTO relatorio = service.gerar(INICIO, FIM);

        // Receita: PAGO por dataPagamento, inclusive de paciente inativo (o dinheiro entrou).
        assertThat(relatorio.receita().quantidadePagamentos()).isEqualTo(3);
        assertThat(relatorio.receita().total()).isEqualByComparingTo("620.00");
        // Inadimplência: em aberto, vencimento no período e já passado — PENDENTE vencido inclusive.
        assertThat(relatorio.inadimplencia().quantidadePagamentos()).isEqualTo(2);
        assertThat(relatorio.inadimplencia().total()).isEqualByComparingTo("430.00");

        assertThat(relatorio.repasses().profissionais())
                .extracting(
                        RelatorioFinanceiroDTO.RepasseProfissional::nome,
                        RelatorioFinanceiroDTO.RepasseProfissional::totalAulas,
                        r -> r.totalProfissional().toPlainString())
                .containsExactly(tuple("Ana", 3L, "60.00"), tuple("Paula", 5L, "112.50"));
        assertThat(relatorio.repasses().totalAulas()).isEqualTo(8);
        assertThat(relatorio.repasses().total()).isEqualByComparingTo("172.50");
        assertThat(relatorio.resultadoLiquido()).isEqualByComparingTo("447.50");

        // Critério de aceite: o consolidado é a soma dos relatórios individuais de todos os profissionais.
        var individuais = Stream.of(paula, ana, bruno, carla)
                .map(p -> profissionalService.gerarRelatorioPagamento(p.getId(), INICIO, FIM))
                .toList();
        assertThat(individuais.stream()
                        .map(r -> r.resumo().totalProfissional())
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(relatorio.repasses().total());
        assertThat(individuais.stream().mapToLong(r -> r.resumo().totalAulas()).sum())
                .isEqualTo(relatorio.repasses().totalAulas());
    }

    @Test
    void gerar_periodoSemMovimento_retornaZeros() {
        montarMassa();

        RelatorioFinanceiroDTO relatorio = service.gerar(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30));

        assertThat(relatorio.receita().quantidadePagamentos()).isZero();
        assertThat(relatorio.receita().total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(relatorio.repasses().profissionais()).isEmpty();
        assertThat(relatorio.repasses().total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(relatorio.inadimplencia().quantidadePagamentos()).isZero();
        assertThat(relatorio.inadimplencia().total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(relatorio.resultadoLiquido()).isEqualTo(new BigDecimal("0.00"));
    }

    private void montarMassa() {
        paula = entityManager.persist(profissional("Paula", "45.00", true));
        ana = entityManager.persist(profissional("Ana", "40.00", false));
        bruno = entityManager.persist(profissional("Bruno", "50.00", true));
        carla = entityManager.persist(profissional("Carla", "50.00", true));
        Paciente p1 = entityManager.persist(paciente(true));
        Paciente p2 = entityManager.persist(paciente(true));
        Paciente inativo = entityManager.persist(paciente(false));

        // Pago antes do período: fora da receita, mas as aulas de fevereiro geram repasse (8 aulas, base 50,00).
        Pagamento pg1 = pagamento(p1, StatusPagamento.PAGO, "400.00", LocalDate.of(2026, 1, 28), INICIO);
        aulas(p1, pg1, paula, true, 1, 26, 1, 27);
        aulas(p1, pg1, paula, true, 2, 2, 2, 3, 2, 4, 2, 5);
        aulas(p1, pg1, null, false, 2, 9, 2, 10);

        // Pago no período (6 aulas, base 50,00): 3 da Ana (inativa, mas com repasse) e 1 da Paula.
        Pagamento pg2 = pagamento(p2, StatusPagamento.PAGO, "300.00", LocalDate.of(2026, 2, 5), INICIO);
        aulas(p2, pg2, ana, true, 2, 2, 2, 3, 2, 4);
        aulas(p2, pg2, paula, true, 2, 5);
        aulas(p2, pg2, null, false, 2, 9, 2, 10);

        // Paciente inativo: entra na receita, mas as aulas não geram repasse — o Bruno some do relatório.
        Pagamento pg3 = pagamento(inativo, StatusPagamento.PAGO, "200.00", LocalDate.of(2026, 2, 10), INICIO);
        aulas(inativo, pg3, bruno, true, 2, 2, 2, 3, 2, 4, 2, 5);

        pagamento(p1, StatusPagamento.PAGO, "150.00", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1));
        pagamento(p2, StatusPagamento.PAGO, "120.00", LocalDate.of(2026, 2, 12), LocalDate.of(2026, 2, 12));

        vencimento(p2, StatusPagamento.VENCIDO, "250.00", LocalDate.of(2026, 2, 10));
        vencimento(p1, StatusPagamento.PENDENTE, "180.00", LocalDate.of(2026, 2, 15));
        // Vence no período, mas ainda não venceu: não é inadimplência.
        vencimento(p1, StatusPagamento.PENDENTE, "999.00", HOJE);
        vencimento(p2, StatusPagamento.VENCIDO, "999.00", LocalDate.of(2026, 1, 31));
        entityManager.flush();
        entityManager.clear();
    }

    private Paciente paciente(boolean ativo) {
        sequencial++;
        Paciente paciente = new Paciente();
        paciente.setNome("Paciente " + sequencial);
        paciente.setEmail("financeiro" + sequencial + "@email.com");
        paciente.setCpf(String.format("%011d", sequencial));
        paciente.setAtivo(ativo);
        return paciente;
    }

    private Profissional profissional(String nome, String percentual, boolean ativo) {
        sequencial++;
        Profissional profissional = new Profissional();
        profissional.setNome(nome);
        profissional.setEmail("prof" + sequencial + "@email.com");
        profissional.setCpf(String.format("9%010d", sequencial));
        profissional.setTipoContrato(TipoContrato.PJ);
        profissional.setPercentualPagamentoAula(new BigDecimal(percentual));
        profissional.setDataInicio(LocalDate.of(2024, 1, 15));
        profissional.setAtivo(ativo);
        return profissional;
    }

    private Pagamento pagamento(
            Paciente paciente, StatusPagamento status, String valor, LocalDate dataPagamento, LocalDate vencimento) {
        Pagamento pagamento = novoPagamento(paciente, status, valor, vencimento);
        pagamento.setDataPagamento(dataPagamento);
        return entityManager.persist(pagamento);
    }

    private void vencimento(Paciente paciente, StatusPagamento status, String valor, LocalDate dataVencimento) {
        entityManager.persist(novoPagamento(paciente, status, valor, dataVencimento));
    }

    /** Um plano por pagamento: a constraint UNIQUE (plano_id, periodo_inicio) recusaria dois no mesmo mês. */
    private Pagamento novoPagamento(Paciente paciente, StatusPagamento status, String valor, LocalDate vencimento) {
        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal(valor));
        plano.setFrequenciaSemanal(FrequenciaSemanal.DUAS_VEZES);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        plano.setDataInicio(LocalDate.of(2025, 1, 1));
        entityManager.persist(plano);

        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal(valor));
        pagamento.setStatus(status);
        pagamento.setDataVencimento(vencimento);
        pagamento.setPeriodoInicio(vencimento.withDayOfMonth(1));
        pagamento.setPeriodoFim(vencimento.withDayOfMonth(vencimento.lengthOfMonth()));
        return pagamento;
    }

    /** Aulas de 2026 nos pares (mês, dia) informados. */
    private void aulas(
            Paciente paciente, Pagamento pagamento, Profissional profissional, boolean realizada, int... mesesDias) {
        for (int i = 0; i < mesesDias.length; i += 2) {
            Aula aula = new Aula();
            aula.setPaciente(paciente);
            aula.setPagamento(pagamento);
            aula.setProfissional(profissional);
            aula.setData(LocalDate.of(2026, mesesDias[i], mesesDias[i + 1]));
            aula.setRealizada(realizada);
            entityManager.persist(aula);
        }
    }
}
