package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.RelatorioFrequenciaDTO;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.RelatorioFrequenciaExporterService;
import com.carlesso.pilatesapi.service.RelatorioFrequenciaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RelatorioFrequenciaController.class)
@AutoConfigureMockMvc(addFilters = false)
class RelatorioFrequenciaControllerTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 30);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RelatorioFrequenciaService service;

    @MockitoBean
    RelatorioFrequenciaExporterService exporter;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void gerar_json_deveRetornarContrato() throws Exception {
        when(service.gerar(INICIO, FIM, null, null, null)).thenReturn(relatorio(null));

        mockMvc.perform(get("/api/relatorios/frequencia")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.inicio").value("2026-11-01"))
                .andExpect(jsonPath("$.consideradoAte").value("2026-11-24"))
                .andExpect(jsonPath("$.filtros.taxaMaxima").isEmpty())
                .andExpect(jsonPath("$.totais.pacientes").value(1))
                .andExpect(jsonPath("$.totais.faltas").value(2))
                .andExpect(jsonPath("$.totais.taxaPresenca").value(75.0))
                .andExpect(jsonPath("$.pacientes[0].pacienteNome").value("Ana Souza"))
                .andExpect(jsonPath("$.pacientes[0].ativo").value(true))
                .andExpect(jsonPath("$.pacientes[0].aulasPrevistas").value(8))
                .andExpect(jsonPath("$.pacientes[0].aulasRealizadas").value(6))
                .andExpect(jsonPath("$.pacientes[0].faltas").value(2))
                .andExpect(jsonPath("$.pacientes[0].taxaPresenca").value(75.0))
                .andExpect(jsonPath("$.pacientes[0].ultimaAulaRealizada").value("2026-11-20"));
    }

    @Test
    void gerar_comFiltros_repassaAoServico() throws Exception {
        when(service.gerar(INICIO, FIM, 5L, 7L, new BigDecimal("75"))).thenReturn(relatorio(new BigDecimal("75")));

        mockMvc.perform(get("/api/relatorios/frequencia")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-30")
                        .param("profissionalId", "5")
                        .param("pacienteId", "7")
                        .param("taxaMaxima", "75"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros.taxaMaxima").value(75));
    }

    @Test
    void exportarXlsx_deveRetornarAnexo() throws Exception {
        when(service.gerar(INICIO, FIM, null, null, null)).thenReturn(relatorio(null));
        when(exporter.exportarXlsx(any())).thenReturn(new byte[] {1, 2, 3});

        mockMvc.perform(get("/api/relatorios/frequencia/xlsx")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                                "Content-Disposition",
                                "attachment; filename=\"relatorio-frequencia-2026-11-01-2026-11-30.xlsx\""))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    @Test
    void gerar_semPeriodo_retorna400() throws Exception {
        mockMvc.perform(get("/api/relatorios/frequencia").param("fim", "2026-11-30"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void gerar_taxaInvalida_retorna400() throws Exception {
        when(service.gerar(eq(INICIO), eq(FIM), isNull(), isNull(), eq(new BigDecimal("150"))))
                .thenThrow(new IllegalArgumentException("taxaMaxima deve estar entre 0 e 100"));

        mockMvc.perform(get("/api/relatorios/frequencia")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-30")
                        .param("taxaMaxima", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("taxaMaxima deve estar entre 0 e 100"));
        mockMvc.perform(get("/api/relatorios/frequencia")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-30")
                        .param("taxaMaxima", "abc"))
                .andExpect(status().isBadRequest());
    }

    private RelatorioFrequenciaDTO relatorio(BigDecimal taxaMaxima) {
        return new RelatorioFrequenciaDTO(
                new PeriodoDTO(INICIO, FIM),
                LocalDate.of(2026, 11, 24),
                new RelatorioFrequenciaDTO.Filtros(null, null, null, taxaMaxima),
                new RelatorioFrequenciaDTO.Totais(1, 8, 6, 2, new BigDecimal("75.0")),
                List.of(new RelatorioFrequenciaDTO.PacienteFrequencia(
                        7L, "Ana Souza", true, 8, 6, 2, new BigDecimal("75.0"), LocalDate.of(2026, 11, 20))),
                LocalDateTime.of(2026, 11, 25, 10, 0));
    }
}
