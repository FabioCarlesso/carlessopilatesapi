package com.carlesso.pilatesapi.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carlesso.pilatesapi.dto.DashboardResumoDTO;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.DashboardService;
import com.carlesso.pilatesapi.service.JwtService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
        when(dashboardService.obterResumo()).thenReturn(resumoCompleto());

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

    @Test
    void resumo_retornaContentTypeJson() throws Exception {
        when(dashboardService.obterResumo()).thenReturn(resumoCompleto());

        mockMvc.perform(get("/dashboard/resumo"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
