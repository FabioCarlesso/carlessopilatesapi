package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.AulasPorProfissionalDTO;
import com.carlesso.pilatesapi.dto.DashboardIndicadoresDTO;
import com.carlesso.pilatesapi.dto.DashboardResumoDTO;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.ContagemMensalProjection;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    PacienteRepository pacienteRepository;

    @Mock
    ProfissionalRepository profissionalRepository;

    @Mock
    PagamentoRepository pagamentoRepository;

    @Mock
    AulaRepository aulaRepository;

    DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(pacienteRepository, profissionalRepository, pagamentoRepository, aulaRepository);
    }

    @Test
    void obterResumo_retornaContadoresCorretos() {
        YearMonth mesAtual = YearMonth.now();
        LocalDate inicioMes = mesAtual.atDay(1);
        LocalDate fimMes = mesAtual.atEndOfMonth();

        when(pacienteRepository.countByAtivoTrue()).thenReturn(8L);
        when(pacienteRepository.countByAtivoFalse()).thenReturn(2L);
        when(profissionalRepository.countByAtivoTrue()).thenReturn(3L);
        when(profissionalRepository.countByAtivoFalse()).thenReturn(1L);
        when(pagamentoRepository.countByStatus(StatusPagamento.PENDENTE)).thenReturn(5L);
        when(pagamentoRepository.countByStatus(StatusPagamento.PAGO)).thenReturn(10L);
        when(pagamentoRepository.countByStatus(StatusPagamento.VENCIDO)).thenReturn(2L);
        when(pagamentoRepository.sumValorByStatusAndDataPagamentoBetween(
                        eq(StatusPagamento.PAGO), eq(inicioMes), eq(fimMes)))
                .thenReturn(new BigDecimal("2000.00"));
        when(aulaRepository.countByRealizadaAndDataBetweenAndPacienteAtivoTrue(eq(true), eq(inicioMes), eq(fimMes)))
                .thenReturn(30L);
        when(aulaRepository.countByRealizadaAndDataBetweenAndPacienteAtivoTrue(eq(false), eq(inicioMes), eq(fimMes)))
                .thenReturn(12L);

        DashboardResumoDTO resumo = service.obterResumo();

        assertThat(resumo.pacientes().totalAtivos()).isEqualTo(8L);
        assertThat(resumo.pacientes().totalInativos()).isEqualTo(2L);
        assertThat(resumo.profissionais().totalAtivos()).isEqualTo(3L);
        assertThat(resumo.profissionais().totalInativos()).isEqualTo(1L);
        assertThat(resumo.pagamentos().totalPendentes()).isEqualTo(5L);
        assertThat(resumo.pagamentos().totalPagos()).isEqualTo(10L);
        assertThat(resumo.pagamentos().totalVencidos()).isEqualTo(2L);
        assertThat(resumo.pagamentos().receitaMesAtual()).isEqualByComparingTo("2000.00");
        assertThat(resumo.aulas().totalRealizadasMesAtual()).isEqualTo(30L);
        assertThat(resumo.aulas().totalAgendadasMesAtual()).isEqualTo(12L);
        assertThat(resumo.geradoEm()).isNotNull();
    }

    @Test
    void obterResumo_semPagamentosNoMes_receitaZero() {
        YearMonth mesAtual = YearMonth.now();
        LocalDate inicioMes = mesAtual.atDay(1);
        LocalDate fimMes = mesAtual.atEndOfMonth();

        when(pacienteRepository.countByAtivoTrue()).thenReturn(0L);
        when(pacienteRepository.countByAtivoFalse()).thenReturn(0L);
        when(profissionalRepository.countByAtivoTrue()).thenReturn(0L);
        when(profissionalRepository.countByAtivoFalse()).thenReturn(0L);
        when(pagamentoRepository.countByStatus(any())).thenReturn(0L);
        when(pagamentoRepository.sumValorByStatusAndDataPagamentoBetween(
                        eq(StatusPagamento.PAGO), eq(inicioMes), eq(fimMes)))
                .thenReturn(BigDecimal.ZERO);
        when(aulaRepository.countByRealizadaAndDataBetweenAndPacienteAtivoTrue(anyBoolean(), eq(inicioMes), eq(fimMes)))
                .thenReturn(0L);

        DashboardResumoDTO resumo = service.obterResumo();

        assertThat(resumo.pagamentos().receitaMesAtual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resumo.aulas().totalRealizadasMesAtual()).isZero();
        assertThat(resumo.aulas().totalAgendadasMesAtual()).isZero();
    }

    @Test
    void obterResumo_geradoEmEhPreenchido() {
        when(pacienteRepository.countByAtivoTrue()).thenReturn(1L);
        when(pacienteRepository.countByAtivoFalse()).thenReturn(0L);
        when(profissionalRepository.countByAtivoTrue()).thenReturn(1L);
        when(profissionalRepository.countByAtivoFalse()).thenReturn(0L);
        when(pagamentoRepository.countByStatus(any())).thenReturn(0L);
        when(pagamentoRepository.sumValorByStatusAndDataPagamentoBetween(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(aulaRepository.countByRealizadaAndDataBetweenAndPacienteAtivoTrue(anyBoolean(), any(), any()))
                .thenReturn(0L);

        DashboardResumoDTO resumo = service.obterResumo();

        assertThat(resumo.geradoEm()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // indicadores
    // -------------------------------------------------------------------------

    @Test
    void obterIndicadores_preencheTodosOsMesesDaJanelaEmOrdemCrescente() {
        YearMonth mesAtual = YearMonth.now();
        YearMonth primeiro = mesAtual.minusMonths(11);
        LocalDate inicio = primeiro.atDay(1);
        LocalDate fim = mesAtual.atEndOfMonth();
        YearMonth meio = mesAtual.minusMonths(3);

        when(pagamentoRepository.sumValorPorMes(StatusPagamento.PAGO, inicio, fim))
                .thenReturn(List.of(receita(meio, "750.00"), receita(mesAtual, "250.00")));
        when(aulaRepository.countPorMes(inicio, fim)).thenReturn(List.of(aulas(meio, 8, 2)));
        when(pacienteRepository.countCadastradosPorMes(inicio, fim)).thenReturn(List.of(contagem(primeiro, 3)));
        when(pacienteRepository.countInativadosPorMes(inicio, fim)).thenReturn(List.of(contagem(meio, 1)));

        DashboardIndicadoresDTO indicadores = service.obterIndicadores(12);

        assertThat(indicadores.meses()).isEqualTo(12);
        assertThat(indicadores.receita())
                .extracting(DashboardIndicadoresDTO.ReceitaMensal::mes)
                .hasSize(12)
                .first()
                .isEqualTo(primeiro);
        assertThat(indicadores.receita())
                .extracting(DashboardIndicadoresDTO.ReceitaMensal::mes)
                .last()
                .isEqualTo(mesAtual);
        assertThat(indicadores.receita().get(8).receita()).isEqualByComparingTo("750.00");
        assertThat(indicadores.receita().get(11).receita()).isEqualByComparingTo("250.00");
        assertThat(indicadores.receita().get(0).receita()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(indicadores.aulas()).hasSize(12);
        assertThat(indicadores.aulas().get(8)).isEqualTo(new DashboardIndicadoresDTO.AulasMensal(meio, 8, 2));
        assertThat(indicadores.aulas().get(0)).isEqualTo(new DashboardIndicadoresDTO.AulasMensal(primeiro, 0, 0));

        assertThat(indicadores.pacientes()).hasSize(12);
        assertThat(indicadores.pacientes().get(0))
                .isEqualTo(new DashboardIndicadoresDTO.PacientesMensal(primeiro, 3, 0));
        assertThat(indicadores.pacientes().get(8)).isEqualTo(new DashboardIndicadoresDTO.PacientesMensal(meio, 0, 1));
        assertThat(indicadores.geradoEm()).isNotNull();
    }

    @Test
    void obterIndicadores_janelaQueAtravessaAViradaDoAno_mantemAOrdemCronologica() {
        // Com 24 meses a janela sempre cruza pelo menos uma virada de ano, qualquer que seja o mês atual.
        when(pagamentoRepository.sumValorPorMes(any(), any(), any())).thenReturn(List.of());
        when(aulaRepository.countPorMes(any(), any())).thenReturn(List.of());
        when(pacienteRepository.countCadastradosPorMes(any(), any())).thenReturn(List.of());
        when(pacienteRepository.countInativadosPorMes(any(), any())).thenReturn(List.of());

        DashboardIndicadoresDTO indicadores = service.obterIndicadores(24);

        List<YearMonth> meses = indicadores.receita().stream()
                .map(DashboardIndicadoresDTO.ReceitaMensal::mes)
                .toList();
        assertThat(meses).hasSize(24).isSorted().doesNotHaveDuplicates();
        assertThat(meses.get(23)).isEqualTo(meses.get(0).plusMonths(23));
    }

    @Test
    void obterIndicadores_umMes_trazSoOMesAtual() {
        YearMonth mesAtual = YearMonth.now();
        when(pagamentoRepository.sumValorPorMes(StatusPagamento.PAGO, mesAtual.atDay(1), mesAtual.atEndOfMonth()))
                .thenReturn(List.of());
        when(aulaRepository.countPorMes(any(), any())).thenReturn(List.of());
        when(pacienteRepository.countCadastradosPorMes(any(), any())).thenReturn(List.of());
        when(pacienteRepository.countInativadosPorMes(any(), any())).thenReturn(List.of());

        DashboardIndicadoresDTO indicadores = service.obterIndicadores(1);

        assertThat(indicadores.receita())
                .singleElement()
                .isEqualTo(new DashboardIndicadoresDTO.ReceitaMensal(mesAtual, BigDecimal.ZERO));
    }

    @Test
    void obterIndicadores_mesesForaDaFaixa_lancaIllegalArgumentSemConsultar() {
        assertThatThrownBy(() -> service.obterIndicadores(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 e 24");
        assertThatThrownBy(() -> service.obterIndicadores(25)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(pagamentoRepository, aulaRepository, pacienteRepository);
    }

    // -------------------------------------------------------------------------
    // aulas por profissional
    // -------------------------------------------------------------------------

    @Test
    void obterAulasPorProfissional_comPeriodo_repassaORankingDoRepositorio() {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fim = LocalDate.of(2026, 6, 30);
        var ranking = List.of(new AulasPorProfissionalDTO(2L, "Paula", 30), new AulasPorProfissionalDTO(1L, "Ana", 12));
        when(aulaRepository.countRealizadasPorProfissional(inicio, fim)).thenReturn(ranking);

        assertThat(service.obterAulasPorProfissional(inicio, fim)).isEqualTo(ranking);
    }

    @Test
    void obterAulasPorProfissional_semPeriodo_usaOMesAtual() {
        YearMonth mesAtual = YearMonth.now();
        when(aulaRepository.countRealizadasPorProfissional(mesAtual.atDay(1), mesAtual.atEndOfMonth()))
                .thenReturn(List.of());

        assertThat(service.obterAulasPorProfissional(null, null)).isEmpty();
    }

    @Test
    void obterAulasPorProfissional_periodoInvalido_lancaIllegalArgument() {
        LocalDate hoje = LocalDate.of(2026, 6, 1);

        assertThatThrownBy(() -> service.obterAulasPorProfissional(hoje, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.obterAulasPorProfissional(hoje, hoje.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.obterAulasPorProfissional(hoje, hoje.plusDays(366)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
        verifyNoInteractions(aulaRepository);
    }

    private static PagamentoRepository.ReceitaMensalProjection receita(YearMonth mes, String total) {
        return new PagamentoRepository.ReceitaMensalProjection() {
            public Integer getAno() {
                return mes.getYear();
            }

            public Integer getMes() {
                return mes.getMonthValue();
            }

            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }

    private static AulaRepository.AulasMensalProjection aulas(YearMonth mes, long realizadas, long agendadas) {
        return new AulaRepository.AulasMensalProjection() {
            public Integer getAno() {
                return mes.getYear();
            }

            public Integer getMes() {
                return mes.getMonthValue();
            }

            public Long getRealizadas() {
                return realizadas;
            }

            public Long getAgendadas() {
                return agendadas;
            }
        };
    }

    private static ContagemMensalProjection contagem(YearMonth mes, long total) {
        return new ContagemMensalProjection() {
            public Integer getAno() {
                return mes.getYear();
            }

            public Integer getMes() {
                return mes.getMonthValue();
            }

            public Long getTotal() {
                return total;
            }
        };
    }
}
