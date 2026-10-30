package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carlesso.pilatesapi.dto.AulasPorProfissionalDTO;
import com.carlesso.pilatesapi.dto.DashboardIndicadoresDTO;
import com.carlesso.pilatesapi.dto.DashboardResumoDTO;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.DashboardService;
import com.carlesso.pilatesapi.service.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DashboardService dashboardService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    private DashboardResumoDTO resumoCompleto() {
        return new DashboardResumoDTO(
                new DashboardResumoDTO.PacientesResumo(8L, 2L),
                new DashboardResumoDTO.ProfissionaisResumo(3L, 1L),
                new DashboardResumoDTO.PagamentosResumo(5L, 10L, 2L, new BigDecimal("2000.00")),
                new DashboardResumoDTO.AulasResumo(30L, 12L),
                LocalDateTime.of(2026, 4, 29, 10, 0, 0));
    }

    @Test
    void resumo_retorna200ComEstrutura() throws Exception {
        when(dashboardService.obterResumo(anyBoolean())).thenReturn(resumoCompleto());

        mockMvc.perform(get("/dashboard/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pacientes.totalAtivos").value(8))
                .andExpect(jsonPath("$.pacientes.totalInativos").value(2))
                .andExpect(jsonPath("$.profissionais.totalAtivos").value(3))
                .andExpect(jsonPath("$.profissionais.totalInativos").value(1))
                .andExpect(jsonPath("$.pagamentos.totalPendentes").value(5))
                .andExpect(jsonPath("$.pagamentos.totalPagos").value(10))
                .andExpect(jsonPath("$.pagamentos.totalVencidos").value(2))
                .andExpect(jsonPath("$.pagamentos.receitaMesAtual").value(2000.00))
                .andExpect(jsonPath("$.aulas.totalRealizadasMesAtual").value(30))
                .andExpect(jsonPath("$.aulas.totalAgendadasMesAtual").value(12))
                .andExpect(jsonPath("$.geradoEm").exists());
    }

    @ParameterizedTest
    @CsvSource({"ADMIN, true", "FINANCEIRO, true", "PROFISSIONAL, false", "RECEPCAO, false"})
    void resumo_soPedeReceitaParaAdminEFinanceiro(String papel, boolean comReceita) throws Exception {
        when(dashboardService.obterResumo(anyBoolean())).thenReturn(resumoCompleto());
        var auth = new UsernamePasswordAuthenticationToken(
                "user@email.com", null, List.of(new SimpleGrantedAuthority("ROLE_" + papel)));

        mockMvc.perform(get("/dashboard/resumo").principal(auth)).andExpect(status().isOk());

        verify(dashboardService).obterResumo(comReceita);
    }

    @Test
    void resumo_retornaContentTypeJson() throws Exception {
        when(dashboardService.obterResumo(anyBoolean())).thenReturn(resumoCompleto());

        mockMvc.perform(get("/dashboard/resumo"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void indicadores_semParametro_usaDozeMesesESerializaOMesComoAnoMes() throws Exception {
        YearMonth janeiro = YearMonth.of(2026, 1);
        when(dashboardService.obterIndicadores(12))
                .thenReturn(new DashboardIndicadoresDTO(
                        12,
                        List.of(new DashboardIndicadoresDTO.ReceitaMensal(janeiro, new BigDecimal("1500.00"))),
                        List.of(new DashboardIndicadoresDTO.AulasMensal(janeiro, 40, 5)),
                        List.of(new DashboardIndicadoresDTO.PacientesMensal(janeiro, 3, 1)),
                        LocalDateTime.of(2026, 1, 20, 10, 0, 0)));

        mockMvc.perform(get("/dashboard/indicadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meses").value(12))
                .andExpect(jsonPath("$.receita[0].mes").value("2026-01"))
                .andExpect(jsonPath("$.receita[0].receita").value(1500.00))
                .andExpect(jsonPath("$.aulas[0].mes").value("2026-01"))
                .andExpect(jsonPath("$.aulas[0].realizadas").value(40))
                .andExpect(jsonPath("$.aulas[0].agendadas").value(5))
                .andExpect(jsonPath("$.pacientes[0].novos").value(3))
                .andExpect(jsonPath("$.pacientes[0].inativados").value(1))
                .andExpect(jsonPath("$.geradoEm").exists());
    }

    @Test
    void indicadores_repassaOParametroMeses() throws Exception {
        when(dashboardService.obterIndicadores(6))
                .thenReturn(new DashboardIndicadoresDTO(6, List.of(), List.of(), List.of(), LocalDateTime.now()));

        mockMvc.perform(get("/dashboard/indicadores").param("meses", "6")).andExpect(status().isOk());

        verify(dashboardService).obterIndicadores(6);
    }

    @Test
    void indicadores_mesesForaDaFaixa_retorna400ProblemDetail() throws Exception {
        when(dashboardService.obterIndicadores(anyInt()))
                .thenThrow(new IllegalArgumentException("Parâmetro meses deve estar entre 1 e 24"));

        mockMvc.perform(get("/dashboard/indicadores").param("meses", "25"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Parâmetro meses deve estar entre 1 e 24"));
    }

    @Test
    void indicadores_mesesNaoNumerico_retorna400SemChamarOService() throws Exception {
        mockMvc.perform(get("/dashboard/indicadores").param("meses", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

        verifyNoInteractions(dashboardService);
    }

    @Test
    void aulasPorProfissional_retornaRanking() throws Exception {
        LocalDate inicio = LocalDate.of(2026, 1, 1);
        LocalDate fim = LocalDate.of(2026, 6, 30);
        when(dashboardService.obterAulasPorProfissional(inicio, fim))
                .thenReturn(List.of(
                        new AulasPorProfissionalDTO(2L, "Paula Mendes", 30),
                        new AulasPorProfissionalDTO(1L, "Ana", 12)));

        mockMvc.perform(get("/dashboard/aulas-por-profissional")
                        .param("inicio", "2026-01-01")
                        .param("fim", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].profissionalId").value(2))
                .andExpect(jsonPath("$[0].nome").value("Paula Mendes"))
                .andExpect(jsonPath("$[0].totalRealizadas").value(30))
                .andExpect(jsonPath("$[1].profissionalId").value(1));
    }

    @Test
    void aulasPorProfissional_semPeriodo_repassaNulosAoService() throws Exception {
        when(dashboardService.obterAulasPorProfissional(null, null)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/aulas-por-profissional"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void aulasPorProfissional_periodoInvalido_retorna400() throws Exception {
        LocalDate inicio = LocalDate.of(2026, 6, 30);
        LocalDate fim = LocalDate.of(2026, 1, 1);
        when(dashboardService.obterAulasPorProfissional(inicio, fim))
                .thenThrow(new IllegalArgumentException("Período inicial não pode ser maior que o período final"));

        mockMvc.perform(get("/dashboard/aulas-por-profissional")
                        .param("inicio", "2026-06-30")
                        .param("fim", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }
}
