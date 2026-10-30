package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.RelatorioFinanceiroDTO;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.RelatorioFinanceiroExporterService;
import com.carlesso.pilatesapi.service.RelatorioFinanceiroService;
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

@WebMvcTest(RelatorioFinanceiroController.class)
@AutoConfigureMockMvc(addFilters = false)
class RelatorioFinanceiroControllerTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 2, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 2, 28);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RelatorioFinanceiroService service;

    @MockitoBean
    RelatorioFinanceiroExporterService exporter;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void gerar_json_deveRetornarContratoConsolidado() throws Exception {
        when(service.gerar(INICIO, FIM)).thenReturn(relatorio());

        mockMvc.perform(get("/api/relatorios/financeiro")
                        .param("inicio", "2026-02-01")
                        .param("fim", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.inicio").value("2026-02-01"))
                .andExpect(jsonPath("$.receita.quantidadePagamentos").value(4))
                .andExpect(jsonPath("$.receita.total").value(1000.00))
                .andExpect(jsonPath("$.repasses.total").value(90.00))
                .andExpect(jsonPath("$.repasses.totalAulas").value(8))
                .andExpect(jsonPath("$.repasses.profissionais[0].nome").value("Paula Mendes"))
                .andExpect(jsonPath("$.repasses.profissionais[0].totalProfissional")
                        .value(90.00))
                .andExpect(jsonPath("$.inadimplencia.total").value(250.00))
                .andExpect(jsonPath("$.resultadoLiquido").value(910.00));
    }

    @Test
    void exportarPdf_deveRetornarAnexo() throws Exception {
        var relatorio = relatorio();
        when(service.gerar(INICIO, FIM)).thenReturn(relatorio);
        when(exporter.exportarPdf(relatorio)).thenReturn("%PDF".getBytes());

        mockMvc.perform(get("/api/relatorios/financeiro/pdf")
                        .param("inicio", "2026-02-01")
                        .param("fim", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string(
                                "Content-Disposition",
                                "attachment; filename=\"relatorio-financeiro-2026-02-01-2026-02-28.pdf\""))
                .andExpect(content().bytes("%PDF".getBytes()));
    }

    @Test
    void exportarXlsx_deveRetornarAnexo() throws Exception {
        var relatorio = relatorio();
        when(service.gerar(INICIO, FIM)).thenReturn(relatorio);
        when(exporter.exportarXlsx(relatorio)).thenReturn(new byte[] {1, 2, 3});

        mockMvc.perform(get("/api/relatorios/financeiro/xlsx")
                        .param("inicio", "2026-02-01")
                        .param("fim", "2026-02-28"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                                "Content-Disposition",
                                "attachment; filename=\"relatorio-financeiro-2026-02-01-2026-02-28.xlsx\""))
                .andExpect(header().longValue("Content-Length", 3));
    }

    @Test
    void gerar_semPeriodo_deveRetornar400() throws Exception {
        mockMvc.perform(get("/api/relatorios/financeiro").param("inicio", "2026-02-01"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void gerar_periodoInvalido_deveRetornar400ComDetalhe() throws Exception {
        when(service.gerar(any(), any()))
                .thenThrow(new IllegalArgumentException("Período inicial não pode ser maior que o período final"));

        mockMvc.perform(get("/api/relatorios/financeiro")
                        .param("inicio", "2026-03-01")
                        .param("fim", "2026-02-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Período inicial não pode ser maior que o período final"));
    }

    private RelatorioFinanceiroDTO relatorio() {
        var paula = new RelatorioFinanceiroDTO.RepasseProfissional(
                1L, "Paula Mendes", new BigDecimal("45.00"), 8, 2, new BigDecimal("400.00"), new BigDecimal("90.00"));
        return new RelatorioFinanceiroDTO(
                new PeriodoDTO(INICIO, FIM),
                new RelatorioFinanceiroDTO.Receita(4, new BigDecimal("1000.00")),
                new RelatorioFinanceiroDTO.Repasses(8, new BigDecimal("90.00"), List.of(paula)),
                new RelatorioFinanceiroDTO.Inadimplencia(1, new BigDecimal("250.00")),
                new BigDecimal("910.00"),
                LocalDateTime.of(2026, 3, 1, 10, 0));
    }
}
