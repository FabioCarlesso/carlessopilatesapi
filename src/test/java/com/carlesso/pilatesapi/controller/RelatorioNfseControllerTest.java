package com.carlesso.pilatesapi.controller;

import com.carlesso.pilatesapi.dto.RelatorioNfseResponseDTO;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.RelatorioNfseExporterService;
import com.carlesso.pilatesapi.service.RelatorioNfseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RelatorioNfseController.class)
@AutoConfigureMockMvc(addFilters = false)
class RelatorioNfseControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RelatorioNfseService service;

    @MockitoBean
    RelatorioNfseExporterService exporter;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void gerar_json_deveRetornarRelatorio() throws Exception {
        when(service.gerar("04/2026", null)).thenReturn(List.of(item()));

        mockMvc.perform(get("/api/relatorios/nfse")
                        .param("competencia", "04/2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Ana Souza"))
                .andExpect(jsonPath("$[0].cpfCnpj").value("11122233344"))
                .andExpect(jsonPath("$[0].valorPago").value(250.00))
                .andExpect(jsonPath("$[0].competencia").value("04/2026"))
                .andExpect(jsonPath("$[0].notaAnteriorEmitida").value(false));

        verify(service).gerar("04/2026", null);
    }

    @Test
    void gerar_comFiltroNotaAnterior_deveRepassarParametro() throws Exception {
        when(service.gerar("04/2026", true)).thenReturn(List.of(item()));

        mockMvc.perform(get("/api/relatorios/nfse")
                        .param("competencia", "04/2026")
                        .param("notaAnteriorEmitida", "true"))
                .andExpect(status().isOk());

        verify(service).gerar("04/2026", true);
    }

    @Test
    void gerar_csv_deveRetornarArquivo() throws Exception {
        var relatorio = List.of(item());
        when(service.gerar("04/2026", null)).thenReturn(relatorio);
        when(exporter.exportarCsv(relatorio)).thenReturn("Nome\nAna".getBytes());

        mockMvc.perform(get("/api/relatorios/nfse")
                        .param("competencia", "04/2026")
                        .param("formato", "CSV"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"relatorio-nfse-04-2026.csv\""));
    }

    @Test
    void gerar_xlsx_deveRetornarArquivo() throws Exception {
        var relatorio = List.of(item());
        when(service.gerar("04/2026", null)).thenReturn(relatorio);
        when(exporter.exportarXlsx(relatorio)).thenReturn(new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/relatorios/nfse")
                        .param("competencia", "04/2026")
                        .param("formato", "XLSX"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"relatorio-nfse-04-2026.xlsx\""));
    }

    @Test
    void gerar_formatoInvalido_deveRetornar400() throws Exception {
        when(service.gerar("04/2026", null)).thenReturn(List.of());

        mockMvc.perform(get("/api/relatorios/nfse")
                        .param("competencia", "04/2026")
                        .param("formato", "PDF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("formato deve ser JSON, CSV ou XLSX"));
    }

    @Test
    void gerar_semCompetencia_deveRetornar400() throws Exception {
        mockMvc.perform(get("/api/relatorios/nfse"))
                .andExpect(status().isBadRequest());
    }

    private RelatorioNfseResponseDTO item() {
        return new RelatorioNfseResponseDTO(
                "Ana Souza",
                "11122233344",
                new BigDecimal("250.00"),
                "04/2026",
                "Aulas de Pilates - Competência 04/2026",
                false,
                LocalDate.of(2026, 4, 10),
                ""
        );
    }
}
