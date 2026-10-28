package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.AulasPorProfissionalDTO;
import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.ProfissionalPagamentoRelatorioDTO;
import com.carlesso.pilatesapi.dto.ProfissionalResumoDTO;
import com.carlesso.pilatesapi.dto.RelatorioFinanceiroDTO;
import com.carlesso.pilatesapi.dto.ResumoFinanceiroDTO;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository.TotalPagamentosProjection;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelatorioFinanceiroServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 2, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 2, 28);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // 01:30 UTC de 16/03 ainda é 15/03 no estúdio: o "hoje" da inadimplência é o do fuso, não o da JVM.
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-03-16T01:30:00Z"), FUSO);

    @Mock
    PagamentoRepository pagamentoRepository;

    @Mock
    AulaRepository aulaRepository;

    @Mock
    ProfissionalService profissionalService;

    RelatorioFinanceiroService service;

    @BeforeEach
    void setUp() {
        service = new RelatorioFinanceiroService(pagamentoRepository, aulaRepository, profissionalService, RELOGIO);
    }

    @Test
    void gerar_consolidaReceitaRepassesEResultado() {
        when(pagamentoRepository.totalizarRecebidos(INICIO, FIM)).thenReturn(totais(4, "1000.00"));
        when(pagamentoRepository.totalizarVencidos(INICIO, FIM, LocalDate.of(2026, 3, 15)))
                .thenReturn(totais(1, "250.00"));
        when(aulaRepository.countRealizadasPorProfissional(INICIO, FIM))
                .thenReturn(List.of(
                        new AulasPorProfissionalDTO(1L, "Paula", 8),
                        new AulasPorProfissionalDTO(2L, "Ana", 3),
                        new AulasPorProfissionalDTO(3L, "Bruno", 1)));
        when(profissionalService.gerarRelatorioPagamento(1L, INICIO, FIM))
                .thenReturn(individual(1L, "Paula", 8, 2, "400.00", "90.00"));
        when(profissionalService.gerarRelatorioPagamento(2L, INICIO, FIM))
                .thenReturn(individual(2L, "Ana", 3, 1, "200.00", "33.33"));
        // Só aulas de paciente inativo: o relatório individual zera, e o profissional fica de fora.
        when(profissionalService.gerarRelatorioPagamento(3L, INICIO, FIM))
                .thenReturn(individual(3L, "Bruno", 0, 0, "0.00", "0.00"));

        RelatorioFinanceiroDTO relatorio = service.gerar(INICIO, FIM);

        assertThat(relatorio.periodo()).isEqualTo(new PeriodoDTO(INICIO, FIM));
        assertThat(relatorio.receita().quantidadePagamentos()).isEqualTo(4);
        assertThat(relatorio.receita().total()).isEqualByComparingTo("1000.00");
        assertThat(relatorio.repasses().total()).isEqualByComparingTo("123.33");
        assertThat(relatorio.repasses().totalAulas()).isEqualTo(11);
        assertThat(relatorio.repasses().profissionais())
                .extracting(RelatorioFinanceiroDTO.RepasseProfissional::nome)
                .containsExactly("Ana", "Paula");
        assertThat(relatorio.inadimplencia().quantidadePagamentos()).isEqualTo(1);
        assertThat(relatorio.inadimplencia().total()).isEqualByComparingTo("250.00");
        assertThat(relatorio.resultadoLiquido()).isEqualByComparingTo("876.67");
        assertThat(relatorio.geradoEm()).isEqualTo(LocalDateTime.of(2026, 3, 15, 22, 30));
    }

    @Test
    void gerar_periodoSemMovimento_retornaZerosComDuasCasas() {
        when(pagamentoRepository.totalizarRecebidos(INICIO, FIM)).thenReturn(totais(0, "0"));
        when(pagamentoRepository.totalizarVencidos(INICIO, FIM, LocalDate.of(2026, 3, 15)))
                .thenReturn(totais(0, "0"));
        when(aulaRepository.countRealizadasPorProfissional(INICIO, FIM)).thenReturn(List.of());

        RelatorioFinanceiroDTO relatorio = service.gerar(INICIO, FIM);

        assertThat(relatorio.receita().total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(relatorio.repasses().total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(relatorio.repasses().profissionais()).isEmpty();
        assertThat(relatorio.inadimplencia().total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(relatorio.resultadoLiquido()).isEqualTo(new BigDecimal("0.00"));
        verifyNoInteractions(profissionalService);
    }

    @Test
    void gerar_repasseMaiorQueReceita_resultadoNegativo() {
        when(pagamentoRepository.totalizarRecebidos(INICIO, FIM)).thenReturn(totais(0, "0"));
        when(pagamentoRepository.totalizarVencidos(INICIO, FIM, LocalDate.of(2026, 3, 15)))
                .thenReturn(totais(0, "0"));
        when(aulaRepository.countRealizadasPorProfissional(INICIO, FIM))
                .thenReturn(List.of(new AulasPorProfissionalDTO(1L, "Paula", 8)));
        when(profissionalService.gerarRelatorioPagamento(1L, INICIO, FIM))
                .thenReturn(individual(1L, "Paula", 8, 2, "400.00", "90.00"));

        assertThat(service.gerar(INICIO, FIM).resultadoLiquido()).isEqualByComparingTo("-90.00");
        verify(profissionalService).gerarRelatorioPagamento(1L, INICIO, FIM);
    }

    @Test
    void gerar_periodoInvertido_recusaSemConsultar() {
        assertThatThrownBy(() -> service.gerar(FIM, INICIO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Período inicial não pode ser maior que o período final");
        verifyNoInteractions(pagamentoRepository, aulaRepository, profissionalService);
    }

    @Test
    void gerar_periodoIncompleto_recusa() {
        assertThatThrownBy(() -> service.gerar(INICIO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Período inicial e final são obrigatórios");
    }

    @Test
    void gerar_periodoAcimaDe366Dias_recusa() {
        assertThatThrownBy(() -> service.gerar(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Consulta por período limitada a 366 dias");
        verifyNoInteractions(pagamentoRepository);
    }

    @Test
    void construtorDoSpring_usaOFusoConfigurado() {
        var doSpring = new RelatorioFinanceiroService(
                pagamentoRepository, aulaRepository, profissionalService, "America/Sao_Paulo");
        when(pagamentoRepository.totalizarRecebidos(INICIO, FIM)).thenReturn(totais(0, "0"));
        when(pagamentoRepository.totalizarVencidos(eq(INICIO), eq(FIM), any())).thenReturn(totais(0, "0"));
        when(aulaRepository.countRealizadasPorProfissional(INICIO, FIM)).thenReturn(List.of());

        assertThat(doSpring.gerar(INICIO, FIM).geradoEm()).isNotNull();
    }

    private static TotalPagamentosProjection totais(long quantidade, String total) {
        return new TotalPagamentosProjection() {
            @Override
            public long getQuantidade() {
                return quantidade;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }

    private static ProfissionalPagamentoRelatorioDTO individual(
            Long id, String nome, long aulas, long pagamentos, String bruto, String repasse) {
        return new ProfissionalPagamentoRelatorioDTO(
                new ProfissionalResumoDTO(id, nome, "000", TipoContrato.PJ, new BigDecimal("45.00"), null),
                new PeriodoDTO(INICIO, FIM),
                new ResumoFinanceiroDTO(aulas, pagamentos, new BigDecimal(bruto), new BigDecimal(repasse)),
                List.of(),
                List.of(),
                LocalDateTime.of(2026, 3, 15, 22, 30));
    }
}
