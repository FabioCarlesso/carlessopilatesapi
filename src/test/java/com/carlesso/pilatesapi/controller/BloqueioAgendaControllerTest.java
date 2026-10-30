package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.BloqueioAgendaResponseDTO;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.service.BloqueioAgendaService;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BloqueioAgendaController.class)
@AutoConfigureMockMvc(addFilters = false)
class BloqueioAgendaControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    BloqueioAgendaService service;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void criar_deveRetornar201ComLocation() throws Exception {
        when(service.criar(any())).thenReturn(bloqueio());

        mockMvc.perform(post("/bloqueios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/bloqueios/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.dataInicio").value("2026-09-07"))
                .andExpect(jsonPath("$.diaInteiro").value(true))
                .andExpect(jsonPath("$.motivo").value("Feriado da Independência"));
    }

    @Test
    void criar_semMotivo_deveRetornar400ComCampo() throws Exception {
        var corpo = corpo();
        corpo.remove("motivo");

        mockMvc.perform(post("/bloqueios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dados inválidos"))
                .andExpect(jsonPath("$.campos[?(@.campo == 'motivo')].mensagem").value("motivo é obrigatório"));
    }

    @Test
    void criar_motivoEmBranco_deveRetornar400ComCampo() throws Exception {
        var corpo = corpo();
        corpo.put("motivo", "   ");

        mockMvc.perform(post("/bloqueios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[?(@.campo == 'motivo')].mensagem").value("motivo é obrigatório"));
    }

    @Test
    void criar_semDataInicio_deveRetornar400ComCampo() throws Exception {
        var corpo = corpo();
        corpo.remove("dataInicio");

        mockMvc.perform(post("/bloqueios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos[?(@.campo == 'dataInicio')].mensagem")
                        .value("dataInicio é obrigatória"));
    }

    @Test
    void criar_periodoInvertido_deveRetornar400ComMensagemDoServico() throws Exception {
        when(service.criar(any()))
                .thenThrow(new IllegalArgumentException("dataFim não pode ser anterior a dataInicio"));

        mockMvc.perform(post("/bloqueios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("dataFim não pode ser anterior a dataInicio"));
    }

    @Test
    void listarPorPeriodo_deveRetornarLista() throws Exception {
        when(service.listarPorPeriodo(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(bloqueio()));

        mockMvc.perform(get("/bloqueios").param("inicio", "2026-09-01").param("fim", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].motivo").value("Feriado da Independência"));
    }

    @Test
    void listarPorPeriodo_semPeriodo_deveRetornar400() throws Exception {
        mockMvc.perform(get("/bloqueios")).andExpect(status().isBadRequest());
    }

    @Test
    void listarPorPeriodo_periodoInvertido_deveRetornar400() throws Exception {
        when(service.listarPorPeriodo(any(), any()))
                .thenThrow(new IllegalArgumentException("Período inicial não pode ser maior que o período final"));

        mockMvc.perform(get("/bloqueios").param("inicio", "2026-09-30").param("fim", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Período inicial não pode ser maior que o período final"));
    }

    @Test
    void buscar_inexistente_deveRetornar404() throws Exception {
        when(service.buscarPorId(9L)).thenThrow(new ResourceNotFoundException("Bloqueio de agenda não encontrado: 9"));

        mockMvc.perform(get("/bloqueios/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Bloqueio de agenda não encontrado: 9"));
    }

    @Test
    void atualizar_deveRetornar200() throws Exception {
        when(service.atualizar(eq(5L), any())).thenReturn(bloqueio());

        mockMvc.perform(put("/bloqueios/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void atualizar_inexistente_deveRetornar404() throws Exception {
        when(service.atualizar(eq(9L), any()))
                .thenThrow(new ResourceNotFoundException("Bloqueio de agenda não encontrado: 9"));

        mockMvc.perform(put("/bloqueios/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo())))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluir_deveRetornar204() throws Exception {
        mockMvc.perform(delete("/bloqueios/5")).andExpect(status().isNoContent());
    }

    @Test
    void excluir_inexistente_deveRetornar404() throws Exception {
        doThrow(new ResourceNotFoundException("Bloqueio de agenda não encontrado: 9"))
                .when(service)
                .excluir(9L);

        mockMvc.perform(delete("/bloqueios/9")).andExpect(status().isNotFound());
    }

    @Test
    void criar_comFaixaDeHorario_deveExporAsHorasNaResposta() throws Exception {
        when(service.criar(any()))
                .thenReturn(new BloqueioAgendaResponseDTO(
                        6L,
                        LocalDate.of(2026, 9, 7),
                        LocalDate.of(2026, 9, 7),
                        LocalTime.of(8, 0),
                        LocalTime.of(12, 0),
                        false,
                        "Manutenção dos aparelhos",
                        LocalDateTime.of(2026, 8, 10, 10, 0),
                        null));
        var corpo = corpo();
        corpo.put("horaInicio", "08:00");
        corpo.put("horaFim", "12:00");
        corpo.put("motivo", "Manutenção dos aparelhos");

        mockMvc.perform(post("/bloqueios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.horaInicio").value("08:00:00"))
                .andExpect(jsonPath("$.horaFim").value("12:00:00"))
                .andExpect(jsonPath("$.diaInteiro").value(false));
    }

    private Map<String, Object> corpo() {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("dataInicio", "2026-09-07");
        corpo.put("dataFim", "2026-09-07");
        corpo.put("motivo", "Feriado da Independência");
        return corpo;
    }

    private BloqueioAgendaResponseDTO bloqueio() {
        return new BloqueioAgendaResponseDTO(
                5L,
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 7),
                null,
                null,
                true,
                "Feriado da Independência",
                LocalDateTime.of(2026, 8, 10, 10, 0),
                null);
    }
}
