package com.carlesso.pilatesapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.dto.DisponibilidadeProfissionalResponseDTO;
import com.carlesso.pilatesapi.dto.VerificacaoDisponibilidadeDTO;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.exception.ConflictException;
import com.carlesso.pilatesapi.exception.ResourceNotFoundException;
import com.carlesso.pilatesapi.exception.TipoProblema;
import com.carlesso.pilatesapi.service.CustomUserDetailsService;
import com.carlesso.pilatesapi.service.DisponibilidadeProfissionalService;
import com.carlesso.pilatesapi.service.JwtService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DisponibilidadeProfissionalController.class)
@AutoConfigureMockMvc(addFilters = false)
class DisponibilidadeProfissionalControllerTest {

    private static final String CORPO =
            """
            {"diaSemana": "MONDAY", "horaInicio": "07:00", "horaFim": "12:00"}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DisponibilidadeProfissionalService service;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    CustomUserDetailsService customUserDetailsService;

    @Test
    void listar_deveRetornarAsFaixasNoContrato() throws Exception {
        when(service.listar(3L)).thenReturn(List.of(faixa()));

        mockMvc.perform(get("/profissionais/3/disponibilidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].profissionalId").value(3))
                .andExpect(jsonPath("$[0].diaSemana").value("MONDAY"))
                .andExpect(jsonPath("$[0].horaInicio").value("07:00:00"))
                .andExpect(jsonPath("$[0].horaFim").value("12:00:00"))
                .andExpect(jsonPath("$[0].version").value(0));
    }

    @Test
    void criar_deveRetornar201ComLocationNaRotaDaFaixa() throws Exception {
        when(service.criar(eq(3L), any())).thenReturn(faixa());

        mockMvc.perform(post("/profissionais/3/disponibilidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/disponibilidades/10"))
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void criar_semDiaSemana_deveRetornar400ComCampo() throws Exception {
        mockMvc.perform(
                        post("/profissionais/3/disponibilidades")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"horaInicio": "07:00", "horaFim": "12:00"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.campos[?(@.campo == 'diaSemana')].mensagem").value("diaSemana é obrigatório"));
    }

    @Test
    void criar_diaSemanaInvalido_deveRetornar400() throws Exception {
        mockMvc.perform(
                        post("/profissionais/3/disponibilidades")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"diaSemana": "SEGUNDA", "horaInicio": "07:00", "horaFim": "12:00"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_sobreposta_deveRetornar409() throws Exception {
        when(service.criar(eq(3L), any())).thenThrow(new ConflictException("se sobrepõe"));

        mockMvc.perform(post("/profissionais/3/disponibilidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(TipoProblema.CONFLITO.uri().toString()));
    }

    @Test
    void criar_faixaInvertida_deveRetornar422() throws Exception {
        when(service.criar(eq(3L), any())).thenThrow(new BusinessException("horaInicio deve ser anterior a horaFim"));

        mockMvc.perform(post("/profissionais/3/disponibilidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("horaInicio deve ser anterior a horaFim"));
    }

    @Test
    void verificar_deveRepassarDataEHorario() throws Exception {
        when(service.verificar(3L, LocalDate.of(2026, 11, 2), LocalTime.of(13, 0)))
                .thenReturn(new VerificacaoDisponibilidadeDTO(false));

        mockMvc.perform(get("/profissionais/3/disponibilidades/verificar")
                        .param("data", "2026-11-02")
                        .param("horario", "13:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dentroDaDisponibilidade").value(false));
    }

    @Test
    void verificar_semHorario_deveRetornar400() throws Exception {
        mockMvc.perform(get("/profissionais/3/disponibilidades/verificar").param("data", "2026-11-02"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verificar_horarioMalformado_deveRetornar400() throws Exception {
        mockMvc.perform(get("/profissionais/3/disponibilidades/verificar")
                        .param("data", "2026-11-02")
                        .param("horario", "13h"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizar_deveRetornar200() throws Exception {
        when(service.atualizar(eq(10L), any())).thenReturn(faixa());

        mockMvc.perform(put("/disponibilidades/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaSemana").value("MONDAY"));
    }

    @Test
    void excluir_deveRetornar204() throws Exception {
        mockMvc.perform(delete("/disponibilidades/10")).andExpect(status().isNoContent());

        verify(service).excluir(10L);
    }

    @Test
    void excluir_inexistente_deveRetornar404() throws Exception {
        doThrow(new ResourceNotFoundException("Disponibilidade não encontrada: 9"))
                .when(service)
                .excluir(9L);

        mockMvc.perform(delete("/disponibilidades/9")).andExpect(status().isNotFound());
    }

    private static DisponibilidadeProfissionalResponseDTO faixa() {
        return new DisponibilidadeProfissionalResponseDTO(
                10L,
                3L,
                DayOfWeek.MONDAY,
                LocalTime.of(7, 0),
                LocalTime.of(12, 0),
                0L,
                LocalDateTime.of(2026, 10, 29, 9, 0),
                null);
    }
}
