package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.PeriodoDTO;
import com.carlesso.pilatesapi.dto.RelatorioRecebimentosDTO;
import com.carlesso.pilatesapi.dto.RelatorioRecebimentosDTO.FormaRecebimento;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.RelatorioRecebimentosExporterService;
import com.carlesso.pilatesapi.service.RelatorioRecebimentosService;
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

@WebMvcTest(RelatorioRecebimentosController.class)
@AutoConfigureMockMvc(addFilters = false)
class RelatorioRecebimentosControllerTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 30);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RelatorioRecebimentosService service;

    @MockitoBean
    RelatorioRecebimentosExporterService exporter;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void gerar_json_deveRetornarContrato() throws Exception {
        when(service.gerar(INICIO, FIM, null, null)).thenReturn(relatorio(null, null));

        mockMvc.perform(get("/api/relatorios/recebimentos")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.inicio").value("2026-11-01"))
                .andExpect(jsonPath("$.filtros.formaPagamento").isEmpty())
                .andExpect(jsonPath("$.resumo.quantidadePagamentos").value(1))
                .andExpect(jsonPath("$.resumo.total").value(300.00))
                .andExpect(
                        jsonPath("$.resumo.porFormaPagamento[0].formaPagamento").value("PIX"))
                .andExpect(jsonPath("$.resumo.porFormaPagamento[0].descricao").value("PIX"))
                .andExpect(jsonPath("$.resumo.porFormaPagamento[0].quantidade").value(1))
                .andExpect(jsonPath("$.recebimentos[0].pacienteNome").value("Ana Souza"))
                .andExpect(jsonPath("$.recebimentos[0].formaPagamento").value("PIX"))
                .andExpect(jsonPath("$.recebimentos[0].dataPagamento").value("2026-11-05"))
                .andExpect(jsonPath("$.recebimentos[0].frequenciaSemanal").value("DUAS_VEZES"));
    }

    @Test
    void gerar_comFiltros_repassaAoServico() throws Exception {
        when(service.gerar(INICIO, FIM, FormaRecebimento.NAO_INFORMADA, 7L))
                .thenReturn(relatorio(FormaRecebimento.NAO_INFORMADA, 7L));

        mockMvc.perform(get("/api/relatorios/recebimentos")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-30")
                        .param("formaPagamento", "NAO_INFORMADA")
                        .param("pacienteId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros.formaPagamento").value("NAO_INFORMADA"))
                .andExpect(jsonPath("$.filtros.pacienteId").value(7));
    }

    @Test
    void gerar_formaInvalida_deveRetornar400() throws Exception {
        mockMvc.perform(get("/api/relatorios/recebimentos")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-30")
                        .param("formaPagamento", "BOLETO"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void exportarPdf_deveRetornarAnexo() throws Exception {
        var relatorio = relatorio(FormaRecebimento.PIX, null);
        when(service.gerar(INICIO, FIM, FormaRecebimento.PIX, null)).thenReturn(relatorio);
        when(exporter.exportarPdf(relatorio)).thenReturn("%PDF".getBytes());

        mockMvc.perform(get("/api/relatorios/recebimentos/pdf")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-30")
                        .param("formaPagamento", "PIX"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string(
                                "Content-Disposition",
                                "attachment; filename=\"relatorio-recebimentos-2026-11-01-2026-11-30.pdf\""))
                .andExpect(content().bytes("%PDF".getBytes()));
    }

    @Test
    void exportarXlsx_deveRetornarAnexo() throws Exception {
        var relatorio = relatorio(null, null);
        when(service.gerar(INICIO, FIM, null, null)).thenReturn(relatorio);
        when(exporter.exportarXlsx(relatorio)).thenReturn(new byte[] {1, 2, 3});

        mockMvc.perform(get("/api/relatorios/recebimentos/xlsx")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                                "Content-Disposition",
                                "attachment; filename=\"relatorio-recebimentos-2026-11-01-2026-11-30.xlsx\""))
                .andExpect(header().longValue("Content-Length", 3));
    }

    @Test
    void gerar_semPeriodo_deveRetornar400() throws Exception {
        mockMvc.perform(get("/api/relatorios/recebimentos").param("inicio", "2026-11-01"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void gerar_periodoInvalido_deveRetornar400ComDetalhe() throws Exception {
        when(service.gerar(any(), any(), isNull(), isNull()))
                .thenThrow(new IllegalArgumentException("Período inicial não pode ser maior que o período final"));

        mockMvc.perform(get("/api/relatorios/recebimentos")
                        .param("inicio", "2026-12-01")
                        .param("fim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Período inicial não pode ser maior que o período final"));
    }

    private RelatorioRecebimentosDTO relatorio(FormaRecebimento forma, Long pacienteId) {
        var linha = new RelatorioRecebimentosDTO.Recebimento(
                10L,
                1L,
                "Ana Souza",
                new BigDecimal("300.00"),
                LocalDate.of(2026, 11, 5),
                FormaRecebimento.PIX,
                LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30),
                3L,
                FrequenciaSemanal.DUAS_VEZES);
        var pix = new RelatorioRecebimentosDTO.TotalPorForma(FormaRecebimento.PIX, "PIX", 1, new BigDecimal("300.00"));
        return new RelatorioRecebimentosDTO(
                new PeriodoDTO(INICIO, FIM),
                new RelatorioRecebimentosDTO.Filtros(forma, pacienteId),
                new RelatorioRecebimentosDTO.Resumo(1, new BigDecimal("300.00"), List.of(pix)),
                List.of(linha),
                LocalDateTime.of(2026, 12, 1, 9, 0));
    }
}
