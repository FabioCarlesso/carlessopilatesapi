package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.carlesso.pilatesapi.dto.DashboardResumoDTO;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.repository.AulaRepository;
import com.carlesso.pilatesapi.repository.PacienteRepository;
import com.carlesso.pilatesapi.repository.PagamentoRepository;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
}
