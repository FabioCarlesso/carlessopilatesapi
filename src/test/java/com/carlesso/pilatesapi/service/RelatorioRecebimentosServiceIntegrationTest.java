package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.carlesso.pilatesapi.dto.RelatorioRecebimentosDTO;
import com.carlesso.pilatesapi.dto.RelatorioRecebimentosDTO.FormaRecebimento;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.FormaPagamento;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/** Relatório de recebimentos (#231) contra o PostgreSQL: critério de data, formas, totais e a receita do financeiro. */
@PostgresDataJpaTest
@Import(ProfissionalService.class)
class RelatorioRecebimentosServiceIntegrationTest extends PostgresTestcontainerSupport {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 30);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    PagamentoRepository pagamentoRepository;

    @Autowired
    AulaRepository aulaRepository;

    @Autowired
    ProfissionalService profissionalService;

    @Autowired
    TestEntityManager entityManager;

    RelatorioRecebimentosService service;
    RelatorioFinanceiroService financeiro;

    private int sequencial;
    private Paciente ana;
    private Paciente bruno;

    @BeforeEach
    void setUp() {
        Clock relogio = Clock.fixed(
                LocalDate.of(2026, 11, 25).atTime(10, 0).atZone(FUSO).toInstant(), FUSO);
        service = new RelatorioRecebimentosService(pagamentoRepository, relogio);
        financeiro = new RelatorioFinanceiroService(pagamentoRepository, aulaRepository, profissionalService, relogio);
    }

    @Test
    void gerar_listaPagosPorDataDePagamento_comTotaisPorForma() {
        montarMassa();

        RelatorioRecebimentosDTO relatorio = service.gerar(INICIO, FIM, null, null);

        // Ordem por data de pagamento; o inativo aparece; PENDENTE, VENCIDO e PAGO fora do período não.
        assertThat(relatorio.recebimentos())
                .extracting(
                        RelatorioRecebimentosDTO.Recebimento::pacienteNome,
                        RelatorioRecebimentosDTO.Recebimento::dataPagamento,
                        RelatorioRecebimentosDTO.Recebimento::formaPagamento,
                        r -> r.valor().toPlainString())
                .containsExactly(
                        tuple("Ana", LocalDate.of(2026, 11, 1), FormaRecebimento.PIX, "300.00"),
                        tuple("Bruno", LocalDate.of(2026, 11, 5), FormaRecebimento.NAO_INFORMADA, "250.00"),
                        tuple("Carla", LocalDate.of(2026, 11, 10), FormaRecebimento.DINHEIRO, "200.00"),
                        tuple("Ana", LocalDate.of(2026, 11, 30), FormaRecebimento.PIX, "150.50"));
        assertThat(relatorio.recebimentos().getFirst().frequenciaSemanal()).isEqualTo(FrequenciaSemanal.DUAS_VEZES);
        assertThat(relatorio.resumo().quantidadePagamentos()).isEqualTo(4);
        assertThat(relatorio.resumo().total()).isEqualByComparingTo("900.50");

        assertThat(relatorio.resumo().porFormaPagamento())
                .extracting(
                        RelatorioRecebimentosDTO.TotalPorForma::formaPagamento,
                        RelatorioRecebimentosDTO.TotalPorForma::quantidade,
                        f -> f.total().toPlainString())
                .containsExactly(
                        tuple(FormaRecebimento.PIX, 2L, "450.50"),
                        tuple(FormaRecebimento.DINHEIRO, 1L, "200.00"),
                        tuple(FormaRecebimento.CARTAO_CREDITO, 0L, "0.00"),
                        tuple(FormaRecebimento.CARTAO_DEBITO, 0L, "0.00"),
                        tuple(FormaRecebimento.TRANSFERENCIA, 0L, "0.00"),
                        tuple(FormaRecebimento.NAO_INFORMADA, 1L, "250.00"));
        // Critério de aceite: a soma por forma é o total geral.
        assertThat(relatorio.resumo().porFormaPagamento().stream()
                        .map(RelatorioRecebimentosDTO.TotalPorForma::total)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(relatorio.resumo().total());
    }

    @Test
    void gerar_semFiltros_totalBateComAReceitaDoRelatorioFinanceiro() {
        montarMassa();

        RelatorioRecebimentosDTO relatorio = service.gerar(INICIO, FIM, null, null);
        var receita = financeiro.gerar(INICIO, FIM).receita();

        assertThat(relatorio.resumo().total()).isEqualByComparingTo(receita.total());
        assertThat(relatorio.resumo().quantidadePagamentos()).isEqualTo(receita.quantidadePagamentos());
    }

    @Test
    void gerar_filtros_restringemLinhasETotais() {
        montarMassa();

        var soPix = service.gerar(INICIO, FIM, FormaRecebimento.PIX, null);
        assertThat(soPix.recebimentos()).hasSize(2);
        assertThat(soPix.resumo().total()).isEqualByComparingTo("450.50");
        assertThat(soPix.filtros().formaPagamento()).isEqualTo(FormaRecebimento.PIX);

        var naoInformada = service.gerar(INICIO, FIM, FormaRecebimento.NAO_INFORMADA, null);
        assertThat(naoInformada.recebimentos())
                .extracting(RelatorioRecebimentosDTO.Recebimento::pacienteNome)
                .containsExactly("Bruno");

        var daAna = service.gerar(INICIO, FIM, null, ana.getId());
        assertThat(daAna.recebimentos()).hasSize(2).allMatch(r -> r.pacienteId().equals(ana.getId()));
        assertThat(daAna.resumo().total()).isEqualByComparingTo("450.50");

        var combinados = service.gerar(INICIO, FIM, FormaRecebimento.PIX, bruno.getId());
        assertThat(combinados.recebimentos()).isEmpty();
        assertThat(combinados.resumo().total()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void gerar_periodoInvalido_lancaIllegalArgument() {
        assertThatThrownBy(() -> service.gerar(null, FIM, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.gerar(FIM, INICIO, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.gerar(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
    }

    private void montarMassa() {
        ana = entityManager.persist(paciente("Ana", true));
        bruno = entityManager.persist(paciente("Bruno", true));
        Paciente carla = entityManager.persist(paciente("Carla", false));

        // Limites inclusivos do período.
        pago(ana, "300.00", LocalDate.of(2026, 11, 1), FormaPagamento.PIX);
        pago(ana, "150.50", LocalDate.of(2026, 11, 30), FormaPagamento.PIX);
        // Confirmado sem forma (migração): entra como "não informada".
        pago(bruno, "250.00", LocalDate.of(2026, 11, 5), null);
        // Paciente inativo que pagou no período: aparece.
        pago(carla, "200.00", LocalDate.of(2026, 11, 10), FormaPagamento.DINHEIRO);
        // Fora do período.
        pago(ana, "999.00", LocalDate.of(2026, 10, 31), FormaPagamento.PIX);
        pago(bruno, "999.00", LocalDate.of(2026, 12, 1), FormaPagamento.PIX);
        // Em aberto com vencimento no período: não são recebimentos.
        aberto(ana, StatusPagamento.PENDENTE, LocalDate.of(2026, 11, 15));
        aberto(bruno, StatusPagamento.VENCIDO, LocalDate.of(2026, 11, 3));
        entityManager.flush();
        entityManager.clear();
    }

    private Paciente paciente(String nome, boolean ativo) {
        sequencial++;
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail("recebimentos" + sequencial + "@email.com");
        paciente.setCpf(String.format("%011d", sequencial));
        paciente.setAtivo(ativo);
        return paciente;
    }

    private void pago(Paciente paciente, String valor, LocalDate dataPagamento, FormaPagamento forma) {
        Pagamento pagamento = novoPagamento(paciente, StatusPagamento.PAGO, valor, dataPagamento.withDayOfMonth(1));
        pagamento.setDataPagamento(dataPagamento);
        pagamento.setFormaPagamento(forma);
        entityManager.persist(pagamento);
    }

    private void aberto(Paciente paciente, StatusPagamento status, LocalDate vencimento) {
        entityManager.persist(novoPagamento(paciente, status, "777.00", vencimento));
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
}
