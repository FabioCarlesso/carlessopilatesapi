package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.PendenciaCadastroPaciente;
import com.carlesso.pilatesapi.dto.RelatorioPacientesDTO;
import com.carlesso.pilatesapi.dto.SituacaoPacienteRelatorio;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.service.RelatorioPacientesExporterService;
import com.carlesso.pilatesapi.service.RelatorioPacientesService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
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

@WebMvcTest(RelatorioPacientesController.class)
@AutoConfigureMockMvc(addFilters = false)
class RelatorioPacientesControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RelatorioPacientesService service;

    @MockitoBean
    RelatorioPacientesExporterService exporter;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void gerar_semFiltros_usaAtivosEDevolveContrato() throws Exception {
        when(service.gerar(SituacaoPacienteRelatorio.ATIVOS, null, null, null, null))
                .thenReturn(relatorio(SituacaoPacienteRelatorio.ATIVOS));

        mockMvc.perform(get("/api/relatorios/pacientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtros.situacao").value("ATIVOS"))
                .andExpect(jsonPath("$.totais.total").value(1))
                .andExpect(jsonPath("$.totais.comPendencia").value(1))
                .andExpect(jsonPath("$.pacientes[0].nome").value("Carlos Lima"))
                .andExpect(jsonPath("$.pacientes[0].dataInativacao").isEmpty())
                .andExpect(jsonPath("$.pacientes[0].pendencias[0]").value("SEM_CPF"))
                .andExpect(jsonPath("$.pacientes[0].planoAtivo.diasSemana[1]").value("WEDNESDAY"))
                .andExpect(jsonPath("$.pacientes[0].planoAtivo.horario").value("08:00:00"))
                .andExpect(jsonPath("$.pacientes[0].planoAtivo.valor").value(300.00))
                .andExpect(jsonPath("$.pacientes[0].profissional.nome").value("Paula Mendes"))
                .andExpect(jsonPath("$.geradoEm").value("2026-11-25T10:00:00"));
    }

    @Test
    void gerar_repassaOsFiltros() throws Exception {
        when(service.gerar(SituacaoPacienteRelatorio.TODOS, true, true, 12, 5L))
                .thenReturn(relatorio(SituacaoPacienteRelatorio.TODOS));

        mockMvc.perform(get("/api/relatorios/pacientes")
                        .param("situacao", "TODOS")
                        .param("semPlanoAtivo", "true")
                        .param("cadastroIncompleto", "true")
                        .param("mesNascimento", "12")
                        .param("profissionalId", "5"))
                .andExpect(status().isOk());

        verify(service).gerar(SituacaoPacienteRelatorio.TODOS, true, true, 12, 5L);
    }

    @Test
    void gerar_situacaoInvalida_deveRetornar400() throws Exception {
        mockMvc.perform(get("/api/relatorios/pacientes").param("situacao", "QUALQUER"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void gerar_mesInvalido_deveRetornar400ComDetalhe() throws Exception {
        when(service.gerar(any(), isNull(), isNull(), any(), isNull()))
                .thenThrow(new IllegalArgumentException("mesNascimento deve estar entre 1 e 12"));

        mockMvc.perform(get("/api/relatorios/pacientes").param("mesNascimento", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("mesNascimento deve estar entre 1 e 12"));
    }

    @Test
    void exportarPdf_deveRetornarAnexo() throws Exception {
        var relatorio = relatorio(SituacaoPacienteRelatorio.TODOS);
        when(service.gerar(SituacaoPacienteRelatorio.TODOS, null, null, null, null))
                .thenReturn(relatorio);
        when(exporter.exportarPdf(relatorio)).thenReturn("%PDF".getBytes());

        mockMvc.perform(get("/api/relatorios/pacientes/pdf").param("situacao", "TODOS"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string(
                                "Content-Disposition",
                                "attachment; filename=\"relatorio-pacientes-todos-2026-11-25.pdf\""));
    }

    @Test
    void exportarXlsx_deveRetornarAnexo() throws Exception {
        var relatorio = relatorio(SituacaoPacienteRelatorio.ATIVOS);
        when(service.gerar(SituacaoPacienteRelatorio.ATIVOS, null, null, null, null))
                .thenReturn(relatorio);
        when(exporter.exportarXlsx(relatorio)).thenReturn(new byte[] {1, 2, 3});

        mockMvc.perform(get("/api/relatorios/pacientes/xlsx"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                                "Content-Disposition",
                                "attachment; filename=\"relatorio-pacientes-ativos-2026-11-25.xlsx\""))
                .andExpect(header().longValue("Content-Length", 3));
    }

    private RelatorioPacientesDTO relatorio(SituacaoPacienteRelatorio situacao) {
        var carlos = new RelatorioPacientesDTO.PacienteItem(
                2L,
                "Carlos Lima",
                null,
                "45999990002",
                "carlos@email.com",
                null,
                null,
                true,
                LocalDate.of(2026, 1, 10),
                null,
                new RelatorioPacientesDTO.PlanoAtivo(
                        10L,
                        TipoPagamento.MENSAL,
                        FrequenciaSemanal.DUAS_VEZES,
                        List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                        LocalTime.of(8, 0),
                        new BigDecimal("300.00")),
                new RelatorioPacientesDTO.ProfissionalResumo(5L, "Paula Mendes"),
                List.of(PendenciaCadastroPaciente.SEM_CPF, PendenciaCadastroPaciente.SEM_DATA_NASCIMENTO));
        return new RelatorioPacientesDTO(
                new RelatorioPacientesDTO.Filtros(situacao, false, false, null, null, null),
                new RelatorioPacientesDTO.Totais(1, 1, 0, 1),
                List.of(carlos),
                LocalDateTime.of(2026, 11, 25, 10, 0));
    }
}
