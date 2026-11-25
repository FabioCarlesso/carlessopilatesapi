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
import com.carlesso.pilatesapi.dto.RelatorioAulasSemEvolucaoDTO;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.RelatorioAulasSemEvolucaoExporterService;
import com.carlesso.pilatesapi.service.RelatorioAulasSemEvolucaoService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RelatorioAulasSemEvolucaoController.class)
@AutoConfigureMockMvc(addFilters = false)
class RelatorioAulasSemEvolucaoControllerTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 11, 30);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RelatorioAulasSemEvolucaoService service;

    @MockitoBean
    RelatorioAulasSemEvolucaoExporterService exporter;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void gerar_json_deveRetornarContrato() throws Exception {
        when(service.gerar(INICIO, FIM, null)).thenReturn(relatorio(null, null));

        mockMvc.perform(get("/api/relatorios/aulas-sem-evolucao")
                        .param("inicio", "2026-10-01")
                        .param("fim", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.inicio").value("2026-10-01"))
                .andExpect(jsonPath("$.periodo.fim").value("2026-11-30"))
                .andExpect(jsonPath("$.filtros.profissionalId").isEmpty())
                .andExpect(jsonPath("$.resumo.totalAulas").value(2))
                .andExpect(
                        jsonPath("$.resumo.porProfissional[0].profissionalId").value(5))
                .andExpect(
                        jsonPath("$.resumo.porProfissional[0].profissionalNome").value("Camila"))
                .andExpect(jsonPath("$.resumo.porProfissional[0].quantidade").value(1))
                .andExpect(
                        jsonPath("$.resumo.porProfissional[1].profissionalId").isEmpty())
                .andExpect(
                        jsonPath("$.resumo.porProfissional[1].profissionalNome").value("Sem profissional"))
                .andExpect(jsonPath("$.aulas[0].aulaId").value(11))
                .andExpect(jsonPath("$.aulas[0].data").value("2026-10-05"))
                .andExpect(jsonPath("$.aulas[0].horario").value("08:00:00"))
                .andExpect(jsonPath("$.aulas[0].pacienteId").value(7))
                .andExpect(jsonPath("$.aulas[0].pacienteNome").value("Ana Souza"))
                .andExpect(jsonPath("$.aulas[0].profissionalNome").value("Camila"))
                .andExpect(jsonPath("$.aulas[1].horario").isEmpty())
                .andExpect(jsonPath("$.aulas[1].profissionalId").isEmpty());
    }

    @Test
    void gerar_comProfissional_repassaAoServico() throws Exception {
        when(service.gerar(INICIO, FIM, 5L)).thenReturn(relatorio(5L, "Camila"));

        mockMvc.perform(get("/api/relatorios/aulas-sem-evolucao")
                        .param("inicio", "2026-10-01")
                        .param("fim", "2026-11-30")
                        .param("profissionalId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros.profissionalId").value(5))
                .andExpect(jsonPath("$.filtros.profissionalNome").value("Camila"));
    }

    @Test
    void exportarXlsx_deveRetornarAnexoComNomeDoPeriodo() throws Exception {
        when(service.gerar(INICIO, FIM, null)).thenReturn(relatorio(null, null));
        when(exporter.exportarXlsx(any())).thenReturn(new byte[] {1, 2, 3});
        when(exporter.nomeArquivo(INICIO, FIM)).thenReturn("relatorio-aulas-sem-evolucao-2026-10-01-2026-11-30.xlsx");

        mockMvc.perform(get("/api/relatorios/aulas-sem-evolucao/xlsx")
                        .param("inicio", "2026-10-01")
                        .param("fim", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                "Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                                "Content-Disposition",
                                "attachment; filename=\"relatorio-aulas-sem-evolucao-2026-10-01-2026-11-30.xlsx\""))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    @Test
    void gerar_semPeriodo_retorna400() throws Exception {
        mockMvc.perform(get("/api/relatorios/aulas-sem-evolucao").param("inicio", "2026-10-01"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void gerar_periodoRecusadoPeloServico_retorna400ComDetalhe() throws Exception {
        when(service.gerar(FIM, INICIO, null))
                .thenThrow(new IllegalArgumentException("Período inicial não pode ser maior que o período final"));

        mockMvc.perform(get("/api/relatorios/aulas-sem-evolucao")
                        .param("inicio", "2026-11-30")
                        .param("fim", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Período inicial não pode ser maior que o período final"));
    }

    private RelatorioAulasSemEvolucaoDTO relatorio(Long profissionalId, String profissionalNome) {
        var aulas = List.of(
                new RelatorioAulasSemEvolucaoDTO.AulaSemEvolucao(
                        11L, LocalDate.of(2026, 10, 5), LocalTime.of(8, 0), 7L, "Ana Souza", 5L, "Camila"),
                new RelatorioAulasSemEvolucaoDTO.AulaSemEvolucao(
                        12L, LocalDate.of(2026, 10, 6), null, 8L, "Bruno Lima", null, null));
        return new RelatorioAulasSemEvolucaoDTO(
                new PeriodoDTO(INICIO, FIM),
                new RelatorioAulasSemEvolucaoDTO.Filtros(profissionalId, profissionalNome),
                new RelatorioAulasSemEvolucaoDTO.Resumo(
                        2,
                        List.of(
                                new RelatorioAulasSemEvolucaoDTO.TotalPorProfissional(5L, "Camila", 1),
                                new RelatorioAulasSemEvolucaoDTO.TotalPorProfissional(null, "Sem profissional", 1))),
                aulas,
                LocalDateTime.of(2026, 11, 25, 10, 0));
    }
}
